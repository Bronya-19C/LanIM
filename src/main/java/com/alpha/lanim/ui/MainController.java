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
import java.util.Timer;
import java.util.TimerTask;

public class MainController {

    private final PeerService peerService;
    private final TcpChatService tcpChatService;
    private final SyncService syncService;
    private final FileTransferService fileTransferService;
    private final MessageDao messageDao;
    private final CertManager certManager;
    private final String roomId;

    private VBox messageContainer;
    private ListView<String> peerListView;
    private Label peerLabel;
    private TextField messageField;
    private final ObservableList<Envelope> messageHistory;
    private final Set<String> displayedMessageIds;

    public MainController(PeerService peerService, TcpChatService tcpChatService,
                          SyncService syncService, FileTransferService fileTransferService,
                          CertManager certManager, String roomId) {
        this.peerService = peerService;
        this.tcpChatService = tcpChatService;
        this.syncService = syncService;
        this.fileTransferService = fileTransferService;
        this.certManager = certManager;
        this.roomId = roomId;
        this.messageDao = new MessageDao();
        this.messageHistory = FXCollections.observableArrayList();
        this.displayedMessageIds = new HashSet<>();

        loadMessageHistory();
    }

    private void loadMessageHistory() {
        List<Envelope> history = messageDao.findByRoomId(roomId);
        messageHistory.addAll(history);
        for (Envelope env : history) {
            if (env.getMessageId() != null) {
                displayedMessageIds.add(env.getMessageId());
            }
        }
    }

    public Scene createScene(Stage stage) {
        stage.setTitle("LANIM - " + peerService.getNickname()
                + " @ " + roomId.substring(0, 8) + "...");

        BorderPane root = new BorderPane();

        // Left: peer list
        peerListView = new ListView<>();
        peerListView.setPrefWidth(220);
        peerLabel = new Label("Members (" + (peerService.getRemotePeerCount() + 1) + ")");
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

        // Restore message history into UI
        for (Envelope env : messageHistory) {
            addMessageToView(env);
        }

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

        // Wire up message sending
        Runnable sendMessage = this::sendChatMessage;
        sendButton.setOnAction(e -> sendMessage.run());
        messageField.setOnAction(e -> sendMessage.run());

        // Wire up file sending
        fileButton.setOnAction(e -> sendFile());

        root.setLeft(leftPane);
        root.setCenter(scrollPane);
        root.setBottom(inputBox);

        // Register TcpChatService callback for incoming messages
        tcpChatService.setMessageCallback(this::onIncomingMessage);
        syncService.setSyncListener(messages -> {
            for (Envelope env : messages) {
                onIncomingMessage(env);
            }
        });

        // Start peer list update timer
        startPeerUpdateTimer();

        // Start auto-connect timer
        startAutoConnectTimer();

        Scene scene = new Scene(root, 850, 600);
        stage.setMinWidth(700);
        stage.setMinHeight(450);
        return scene;
    }

    private void sendChatMessage() {
        String text = messageField.getText().trim();
        String error = Validator.validateChatText(text);
        if (error != null) return;

        messageField.clear();

        new Thread(() -> {
            int seq = syncService.nextSequence();
            ChatPayload payload = new ChatPayload(text);

            Envelope envelope = new Envelope(
                    MessageType.CHAT_TEXT.name(),
                    java.util.UUID.randomUUID().toString(),
                    peerService.getLocalPeerId(),
                    roomId,
                    seq,
                    System.currentTimeMillis(),
                    JsonUtil.gson().toJsonTree(payload).getAsJsonObject()
            );

            syncService.recordOutgoingMessage(envelope);
            tcpChatService.broadcast(envelope);

            addMessageToView(envelope);
        }).start();
    }

    private void sendFile() {
        java.io.File file = chooseFile();
        if (file != null) {
            new Thread(() -> {
                try {
                    fileTransferService.sendFile(null, file);
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

    private void startPeerUpdateTimer() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    peerListView.getItems().clear();
                    ObservableList<String> items = peerListView.getItems();
                    items.add(peerService.getNickname() + " (You)");
                    for (Peer p : peerService.getRemotePeers()) {
                        String status = tcpChatService.isConnected(p.getPeerId())
                                ? " [online]" : " [connecting]";
                        items.add(p.getNickname() + status);
                    }
                    peerLabel.setText("Members (" + (peerService.getRemotePeerCount() + 1) + ")");
                });
            }
        }, 0, 1000);
    }

    private void startAutoConnectTimer() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (Peer p : peerService.getRemotePeers()) {
                    if (!tcpChatService.isConnected(p.getPeerId())) {
                        tcpChatService.connectToPeer(p.getPeerId(), p.getAddress(), p.getPort());
                    }
                }
            }
        }, 2000, 2000);
    }
}
