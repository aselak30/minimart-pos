package com.minimartpos.controller.shared;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.util.SceneManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Screen shown when database connection cannot be established on startup.
 * Allows user to enter connection details and retry.
 */
public class DatabaseSetupController implements Initializable {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField dbNameField;
    @FXML private TextField userField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        hostField.setText("localhost");
        portField.setText("3306");
        dbNameField.setText("minimart_pos");
        userField.setText("pos_user");
    }

    @FXML
    private void testConnection() {
        try {
            DatabaseConfig.initialize(
                hostField.getText().trim(),
                Integer.parseInt(portField.getText().trim()),
                dbNameField.getText().trim(),
                userField.getText().trim(),
                passwordField.getText()
            );
            if (DatabaseConfig.isConnected()) {
                statusLabel.setText("✔ Connected successfully!");
                statusLabel.setStyle("-fx-text-fill:-pos-success;");
            }
        } catch (Exception e) {
            statusLabel.setText("✖ " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill:-pos-danger;");
        }
    }

    @FXML
    private void proceed() {
        if (DatabaseConfig.isConnected()) {
            SceneManager.navigateTo("shared/Login.fxml");
        } else {
            statusLabel.setText("Please connect to the database first.");
        }
    }
}
