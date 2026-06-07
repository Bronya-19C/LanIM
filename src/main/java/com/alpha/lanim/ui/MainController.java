package com.alpha.lanim.ui;

import com.alpha.lanim.bll.*;
import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.dal.MessageDao;
import com.alpha.lanim.model.*;
import com.alpha.lanim.util.JsonUtil;
import com.alpha.lanim.util.Validator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainController {

    private final PeerService peerService;
    private final TcpChatService tcpChatService;
    private final FileTransferService fileTransferService;
    private final MessageService messageService;
    private final MessageDao messageDao;
    private final CertManager certManager;

    private VBox messageContainer;
    private ListView<String> peerListView;
    private Label peerLabel;
    private TextField messageField;
    private final ObservableList<Envelope> messageHistory;
    private final Set<String> displayedMessageIds;
    private String roomId;

    public MainController(PeerService peerService, TcpChatService tcpChatService,
                          FileTransferService fileTransferService,
                          MessageService messageService, CertManager certManager) {
        this.peerService = peerService;
        this.tcpChatService = tcpChatService;
        this.fileTransferService = fileTransferService;
        this.messageService = messageService;
        this.certManager = certManager;
        this.messageDao = new MessageDao();
        this.messageHistory = FXCollections.observableArrayList();
        this.displayedMessageIds = new HashSet<>();
    }

    public Scene createScene(Stage stage) {
        stage.setTitle("LANIM - " + peerService.getNickname());

        BorderPane root = new BorderPane();

        // Left: peer list
        peerListView = new ListView<>();
        peerListView.setPrefWidth(220);
        peerLabel = new Label("Members (1)");
        peerLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5 0 5 0;");
        VBox leftPane = new VBox(5);
        leftPane.setPadding(new Insets(10));
        leftPane.getChildren().addAll(peerLabel, peerListView);

        // Center: chat messages
        messageContainer = new VBox(6);
        messageContainer.setPadding(new Insets(10));
        ScrollPane scrollPane = new ScrollPane(messageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Bottom: input area
        HBox inputBox = new HBox(10);
        inputBox.setPadding(new Insets(10));
        messageField = new TextField();
        messageField.setPromptText("Type a message...");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button sendButton = new Button("Send");
        sendButton.setPrefWidth(80);

        Button fileButton = new Button("File");
        fileButton.setPrefWidth(60);
        fileButton.setTooltip(new Tooltip("Send a file"));

        inputBox.getChildren().addAll(messageField, sendButton, fileButton);

        Runnable sendMessage = this::sendChatMessage;
        sendButton.setOnAction(e -> sendMessage.run());
        messageField.setOnAction(e -> sendMessage.run());

        fileButton.setOnAction(e -> sendFile());

        root.setLeft(leftPane);
        root.setCenter(scrollPane);
        root.setBottom(inputBox);

        // Register message callbacks
        tcpChatService.setMessageCallback(this::onIncomingMessage);
        messageService.setJoinCallback(this::onJoinAck);
        messageService.setUserEventCallback(new MessageService.UserEventCallback() {
            @Override
            public void onUserJoined(UserEventPayload payload) {
                Platform.runLater(() -> {
                    peerService.addRemotePeerById(payload.getPeerId(), payload.getNickname());
                    refreshPeerList();
                });
            }

            @Override
            public void onUserLeft(UserEventPayload payload) {
                Platform.runLater(() -> {
                    peerService.removeRemotePeer(payload.getPeerId());
                    refreshPeerList();
                });
            }
        });

        Scene scene = new Scene(root, 850, 600);
        stage.setMinWidth(700);
        stage.setMinHeight(450);
        return scene;
    }

    private void onJoinAck(JoinAckPayload ack) {
        Platform.runLater(() -> {
            this.roomId = ack.getRoomId();
            stageSetRoomId(roomId);

            // Load members
            if (ack.getMembers() != null) {
                for (JoinAckPayload.MemberInfo m : ack.getMembers()) {
                    if (!m.getPeerId().equals(peerService.getLocalPeerId())) {
                        peerService.addRemotePeerById(m.getPeerId(), m.getNickname());
                    }
                }
            }
            refreshPeerList();

            // Load history
            messageHistory.clear();
            displayedMessageIds.clear();
            if (ack.getHistory() != null) {
                for (Envelope env : ack.getHistory()) {
                    if (env.getMessageId() != null) {
                        displayedMessageIds.add(env.getMessageId());
                    }
                    messageHistory.add(env);
                    addMessageToView(env);
                }
            }
        });
    }

    private void stageSetRoomId(String roomId) {
        Platform.runLater(() -> {
            Stage stage = (Stage) messageContainer.getScene().getWindow();
            if (stage != null) {
                stage.setTitle("LANIM - " + peerService.getNickname()
                        + " @ " + roomId.substring(0, Math.min(8, roomId.length())) + "...");
            }
        });
    }

    private void sendChatMessage() {
        String text = messageField.getText().trim();
        String error = Validator.validateChatText(text);
        if (error != null) return;

        messageField.clear();

        new Thread(() -> {
            ChatPayload payload = new ChatPayload(text);

            Envelope envelope = new Envelope(
                    MessageType.CHAT_TEXT.name(),
                    java.util.UUID.randomUUID().toString(),
                    peerService.getLocalPeerId(),
                    roomId != null ? roomId : "",
                    0,
                    System.currentTimeMillis(),
                    JsonUtil.gson().toJsonTree(payload).getAsJsonObject()
            );

            tcpChatService.sendToServer(envelope);
            addMessageToView(envelope);
        }).start();
    }

    private void sendFile() {
        java.io.File file = chooseFile();
        if (file != null) {
            new Thread(() -> {
                try {
                    fileTransferService.sendFile(file);
                    Platform.runLater(() -> {
                        Label label = new Label("You sent file: " + file.getName()
                                + " (" + (file.length() / 1024) + " KB)");
                        label.setStyle("-fx-text-fill: green; -fx-font-style: italic;");
                        messageContainer.getChildren().add(label);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR, "Failed to send file: " + ex.getMessage()).show());
                }
            }).start();
        }
    }

    private void onIncomingMessage(Envelope envelope) {
        if (envelope.getSenderId() == null
                || envelope.getSenderId().equals(peerService.getLocalPeerId())) {
            return;
        }
        addMessageToView(envelope);
    }

    private void addMessageToView(Envelope envelope) {
        Platform.runLater(() -> {
            if (envelope.getMessageId() != null && !displayedMessageIds.add(envelope.getMessageId())) {
                return;
            }

            String type = envelope.getType();
            if (type == null) return;

            switch (type) {
                case "CHAT_TEXT": {
                    ChatPayload payload = JsonUtil.fromPayload(envelope.getPayload(), ChatPayload.class);
                    if (payload == null) return;
                    String displayName = getDisplayName(envelope.getSenderId());
                    Label label = new Label(displayName + ": " + payload.getText());
                    label.setWrapText(true);
                    if (envelope.getSenderId().equals(peerService.getLocalPeerId())) {
                        label.setStyle("-fx-font-size: 13px; -fx-padding: 3 0; -fx-text-fill: #1a1a1a;");
                    } else {
                        label.setStyle("-fx-font-size: 13px; -fx-padding: 3 0; -fx-text-fill: #333;");
                    }
                    messageContainer.getChildren().add(label);
                    break;
                }
                case "FILE_META": {
                    FileMetaPayload meta = JsonUtil.fromPayload(envelope.getPayload(), FileMetaPayload.class);
                    if (meta == null) return;
                    String displayName = getDisplayName(envelope.getSenderId());
                    Label label = new Label(displayName + " sent file: " + meta.getFileName()
                            + " (" + (meta.getTotalSize() / 1024) + " KB)");
                    label.setStyle("-fx-text-fill: #0066cc; -fx-font-style: italic;");
                    label.setWrapText(true);
                    messageContainer.getChildren().add(label);
                    break;
                }
                default:
                    break;
            }
        });
    }

    private String getDisplayName(String peerId) {
        if (peerId == null) return "Unknown";
        if (peerId.equals(peerService.getLocalPeerId())) {
            return peerService.getNickname() + " (You)";
        }
        Peer p = peerService.getRemotePeer(peerId);
        return p != null ? p.getNickname() : peerId.substring(0, Math.min(8, peerId.length()));
    }

    private java.io.File chooseFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select File to Send");
        return chooser.showOpenDialog(null);
    }

    private void refreshPeerList() {
        if (peerListView == null) return;
        peerListView.getItems().clear();
        ObservableList<String> items = peerListView.getItems();
        items.add(peerService.getNickname() + " (You)");
        for (Peer p : peerService.getRemotePeers()) {
            items.add(p.getNickname() + " [online]");
        }
        peerLabel.setText("Members (" + (peerService.getRemotePeerCount() + 1) + ")");
    }
}
