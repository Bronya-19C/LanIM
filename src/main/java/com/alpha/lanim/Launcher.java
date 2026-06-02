package com.alpha.lanim;

import com.alpha.lanim.bll.*;
import com.alpha.lanim.bll.crypto.CertManager;
import com.alpha.lanim.dal.DBUtil;
import com.alpha.lanim.ui.LoginController;
import com.alpha.lanim.ui.MainController;
import com.alpha.lanim.util.HashUtil;
import com.alpha.lanim.util.Constants;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Launcher extends Application {

    private PeerService peerService;
    private CertManager certManager;
    private PeerConnectionManager connectionManager;
    private TcpChatService tcpChatService;
    private FileTransferService fileTransferService;
    private SyncService syncService;
    private MessageService messageService;
    private MdnsDiscoveryService mdnsDiscoveryService;
    private Stage primaryStage;

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

    private void doConnect(String nickname, String roomSecret, boolean useTls) {
        new Thread(() -> {
            try {
                String roomId = HashUtil.sha512Hex(roomSecret);
                String transportMode = useTls
                        ? Constants.TRANSPORT_MODE_TLS
                        : Constants.TRANSPORT_MODE_PLAIN;

                // Phase 1: Create services without cross-dependencies
                peerService = new PeerService();
                connectionManager = new PeerConnectionManager(certManager, transportMode);
                messageService = new MessageService();
                fileTransferService = new FileTransferService(peerService);
                syncService = new SyncService(peerService);

                // Phase 2: Start network
                int port = connectionManager.startServer();
                peerService.init(nickname, roomId, "127.0.0.1", port);

                // Phase 3: Wire cross-dependencies
                tcpChatService = new TcpChatService(connectionManager, messageService);
                fileTransferService.setTcpChatService(tcpChatService);
                syncService.setTcpChatService(tcpChatService);
                syncService.init();
                fileTransferService.setSyncService(syncService);

                // Phase 4: Wire message service handlers
                messageService.setSyncService(syncService);
                messageService.setFileTransferService(fileTransferService);
                messageService.initHandlers();

                // Phase 5: Start discovery and sync
                mdnsDiscoveryService = new MdnsDiscoveryService(peerService);
                mdnsDiscoveryService.start(port);
                syncService.start();

                // Phase 6: Show main window
                Platform.runLater(() -> showMainWindow(roomId));

            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "Connection failed: " + e.getMessage());
                    alert.showAndWait();
                    // Return to login
                    primaryStage.setScene(LoginController.createScene(
                            primaryStage, this::doConnect));
                    primaryStage.show();
                });
            }
        }).start();
    }

    private void showMainWindow(String roomId) {
        MainController controller = new MainController(
                peerService, tcpChatService, syncService,
                fileTransferService, certManager, roomId);

        primaryStage.setScene(controller.createScene(primaryStage));
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();
    }

    private void shutdown() {
        if (mdnsDiscoveryService != null) mdnsDiscoveryService.stop();
        if (syncService != null) syncService.stop();
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
