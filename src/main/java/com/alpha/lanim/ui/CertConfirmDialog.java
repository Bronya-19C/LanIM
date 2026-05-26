package com.alpha.lanim.ui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CertConfirmDialog {

    public static boolean showAndWait(String peerId, String fingerprint) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Certificate Verification");
        dialog.setMinWidth(420);

        VBox root = new VBox(12);
        root.setStyle("-fx-padding: 20; -fx-font-size: 13px;");

        Label title = new Label("Untrusted Peer Certificate");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label infoLabel = new Label(
                "This is the first connection to peer \"" + peerId + "\".\n\n" +
                "Certificate fingerprint:\n" + fingerprint + "\n\n" +
                "Trust this certificate for future connections?");
        infoLabel.setWrapText(true);

        ButtonBar buttonBar = new ButtonBar();
        Button trustButton = new Button("Trust Once");
        Button alwaysButton = new Button("Always Trust");
        Button rejectButton = new Button("Reject");
        ButtonBar.setButtonData(trustButton, ButtonBar.ButtonData.OTHER);
        ButtonBar.setButtonData(alwaysButton, ButtonBar.ButtonData.YES);
        ButtonBar.setButtonData(rejectButton, ButtonBar.ButtonData.NO);

        buttonBar.getButtons().addAll(rejectButton, trustButton, alwaysButton);

        root.getChildren().addAll(title, infoLabel, buttonBar);

        Scene scene = new Scene(root);
        dialog.setScene(scene);

        final boolean[] result = {false};

        trustButton.setOnAction(e -> { result[0] = true; dialog.close(); });
        alwaysButton.setOnAction(e -> { result[0] = true; dialog.close(); });
        rejectButton.setOnAction(e -> { result[0] = false; dialog.close(); });

        dialog.showAndWait();
        return result[0];
    }
}
