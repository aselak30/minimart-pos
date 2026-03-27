package com.minimartpos.util;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Helper methods for showing JavaFX dialogs consistently across the app.
 */
public final class AlertUtil {

    private AlertUtil() {}

    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows a yes/no confirmation dialog.
     * @return true if the user clicked OK/Yes.
     */
    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Shows a text-input dialog and returns the entered text, or empty string.
     */
    public static String promptText(String title, String label, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue != null ? defaultValue : "");
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        return dialog.showAndWait().orElse("").trim();
    }

    /**
     * Shows a numeric input dialog (e.g. for quantity or discount).
     * Returns the parsed value, or -1 if cancelled.
     */
    public static double promptNumber(String title, String label, double defaultValue) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(defaultValue));
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        String result = dialog.showAndWait().orElse("").trim();
        try {
            return Double.parseDouble(result);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
