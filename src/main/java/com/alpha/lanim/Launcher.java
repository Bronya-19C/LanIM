package com.alpha.lanim;

import com.alpha.lanim.bll.*;
import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.dal.DBUtil;
import com.alpha.lanim.model.*;
import com.alpha.lanim.ui.LoginController;
import com.alpha.lanim.ui.MainController;
import com.alpha.lanim.util.JsonUtil;
import com.alpha.lanim.util.Constants;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Launcher extends Application {

    private PeerService peerService;
    private CertManager certManager;
    private ClientConnectionService connectionService;
    private TcpChatService tcpChatService;
    private FileTransferService fileTransferService;
    private MessageService messageService;
    private Stage primaryStage;
    private String serverHost;
    private int serverPort;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        try {
            DBUtil.init();
            certManager = new CertManager();
            certManager.init();
        } catch (Exception e) {
            showFatal("Initialization Error", "Failed to initialize: " + e.getMessage());
            return;
        }

        primaryStage.setScene(LoginController.createScene(stage, this::doConnect));
        primaryStage.show();
    }

    private void doConnect(String nickname, String roomSecret, boolean useTls,
                           String serverAddress) {
        new Thread(() -> {
            try {
                serverHost = "localhost";
                serverPort = Constants.DEFAULT_SERVER_PORT;
                if (serverAddress != null && !serverAddress.trim().isEmpty()) {
                    String[] parts = serverAddress.trim().split(":");
                    serverHost = parts[0].trim();
                    if (parts.length > 1) {
                        serverPort = Integer.parseInt(parts[1].trim());
                    }
                }

                String transportMode = useTls
                        ? Constants.TRANSPORT_MODE_TLS
                        : Constants.TRANSPORT_MODE_PLAIN;

                peerService = new PeerService();
                messageService = new MessageService();
                fileTransferService = new FileTransferService(peerService);
                connectionService = new ClientConnectionService(certManager, transportMode);
                tcpChatService = new TcpChatService(connectionService, messageService);

                messageService.setFileTransferService(fileTransferService);
                fileTransferService.setTcpChatService(tcpChatService);

                connectionService.connect(serverHost, serverPort);

                peerService.init(nickname, "");

                JoinPayload joinPayload = new JoinPayload(
                        peerService.getLocalPeerId(), nickname, roomSecret);

                Envelope joinEnv = new Envelope(
                        MessageType.JOIN.name(),
                        java.util.UUID.randomUUID().toString(),
                        peerService.getLocalPeerId(),
                        "",
                        0,
                        System.currentTimeMillis(),
                        JsonUtil.gson().toJsonTree(joinPayload).getAsJsonObject()
                );

                connectionService.send(JsonUtil.toJsonBytes(joinEnv));

                Platform.runLater(() -> showMainWindow());

            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "Connection failed: " + e.getMessage());
                    alert.showAndWait();
                    primaryStage.setScene(LoginController.createScene(
                            primaryStage, this::doConnect));
                    primaryStage.show();
                });
            }
        }).start();
    }

    private void showMainWindow() {
        MainController controller = new MainController(
                peerService, tcpChatService, fileTransferService,
                messageService, certManager);

        primaryStage.setScene(controller.createScene(primaryStage));
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();
    }

    private void shutdown() {
        if (tcpChatService != null) tcpChatService.shutdown();
    }

    private void showFatal(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message);
            alert.setTitle(title);
            alert.showAndWait();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
