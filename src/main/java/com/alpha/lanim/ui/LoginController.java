package com.alpha.lanim.ui;

import com.alpha.lanim.util.Validator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoginController {

    public interface LoginCallback {
        void onConnect(String nickname, String roomSecret, boolean useTls);
    }

    public static Scene createScene(Stage stage, LoginCallback callback) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-font-size: 14px;");

        Label titleLabel = new Label("LANIM");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label("Local Area Network Instant Messenger");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666;");

        Separator sep = new Separator();
        sep.setMaxWidth(320);

        Label nicknameLabel = new Label("Nickname:");
        TextField nicknameField = new TextField();
        nicknameField.setPromptText("Enter your display name...");
        nicknameField.setMaxWidth(320);
        nicknameField.setText("User-" + Integer.toHexString((int)(Math.random() * 0xFFFF)).toUpperCase());

        Label roomLabel = new Label("Room Secret:");
        PasswordField roomField = new PasswordField();
        roomField.setPromptText("Enter room password...");
        roomField.setMaxWidth(320);

        CheckBox tlsCheckbox = new CheckBox("Enable TLS encryption");
        tlsCheckbox.setSelected(true);

        Button connectButton = new Button("Connect to Room");
        connectButton.setPrefWidth(200);
        connectButton.setDefaultButton(true);

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: red;");

        connectButton.setOnAction(e -> {
            String nickname = nicknameField.getText().trim();
            String roomSecret = roomField.getText().trim();
            boolean useTls = tlsCheckbox.isSelected();

            String error = Validator.validateNickname(nickname);
            if (error != null) { statusLabel.setText(error); return; }
            error = Validator.validateRoomSecret(roomSecret);
            if (error != null) { statusLabel.setText(error); return; }

            connectButton.setDisable(true);
            statusLabel.setStyle("-fx-text-fill: gray;");
            statusLabel.setText("Connecting...");
            callback.onConnect(nickname, roomSecret, useTls);
        });

        root.getChildren().addAll(titleLabel, subtitleLabel, sep,
                nicknameLabel, nicknameField,
                roomLabel, roomField,
                tlsCheckbox, connectButton, statusLabel);

        Scene scene = new Scene(root, 400, 380);
        stage.setTitle("LANIM - Login");
        stage.setMinWidth(400);
        stage.setMinHeight(380);
        return scene;
    }

    public static void showError(Label statusLabel, String message) {
        statusLabel.setStyle("-fx-text-fill: red;");
        statusLabel.setText(message);
    }
}
