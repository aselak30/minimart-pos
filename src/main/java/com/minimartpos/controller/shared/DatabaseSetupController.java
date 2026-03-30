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
    @FXML private Label localIpLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        hostField.setText("localhost");
        portField.setText("3306");
        dbNameField.setText("minimart_pos");
        userField.setText("pos_user");
        
        String ip = getLocalIpAddress();
        if (localIpLabel != null) {
            localIpLabel.setText("Your IP: " + (ip != null ? ip : "Unknown"));
        }
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
                // Save credentials so they are loaded automatically next time
                DatabaseConfig.saveProperties();
                statusLabel.setText("✔ Connected successfully! Settings saved.");
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

    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = 
                java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
