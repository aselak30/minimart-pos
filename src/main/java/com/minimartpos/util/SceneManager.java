package com.minimartpos.util;

import com.minimartpos.config.AppConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Centralized screen/scene navigation manager for JavaFX.
 * Supports:
 *   - Navigate to a new screen (push to stack)
 *   - Navigate back (pop from stack)
 *   - Open modal dialogs
 *   - FXML caching for performance
 *
 * Usage:
 *   SceneManager.navigateTo("admin/Dashboard.fxml");
 *   SceneManager.navigateBack();
 *   SceneManager.openModal("shared/Confirm.fxml", "Confirm Action");
 */
public final class SceneManager {

    private static final Logger logger = LogManager.getLogger(SceneManager.class);

    private static Stage  primaryStage;
    private static Scene  currentScene;

    /** Navigation stack for back-navigation support */
    private static final Stack<String> navigationStack = new Stack<>();

    /** Optional: cache loaded FXML roots to avoid re-parsing */
    private static final Map<String, Parent> fxmlCache = new HashMap<>();

    private SceneManager() {}

    /**
     * Must be called once in MainApp.start() before any navigation.
     */
    public static void initialize(Stage stage) {
        primaryStage = stage;
        currentScene = new Scene(new javafx.scene.layout.StackPane(),
                                 AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
        primaryStage.setScene(currentScene);
        applyGlobalStylesheet(currentScene);
    }

    /**
     * Navigate to an FXML screen, pushing current to the stack.
     *
     * @param fxmlPath  Relative path under /fxml/, e.g. "admin/Dashboard.fxml"
     */
    public static void navigateTo(String fxmlPath) {
        try {
            Parent root = loadFXML(fxmlPath);
            currentScene.setRoot(root);
            navigationStack.push(fxmlPath);
            // Apply user theme after every navigation
            try { com.minimartpos.util.ThemeManager.applyCurrentUserTheme(currentScene); }
            catch (Exception ignored) {}
            logger.debug("Navigated to: {}", fxmlPath);
        } catch (IOException e) {
            logger.error("Failed to navigate to {}: {}", fxmlPath, e.getMessage(), e);
            showNavError(fxmlPath, e);
        } catch (Exception e) {
            logger.error("Screen load error for {}: {}", fxmlPath, e.getMessage(), e);
            showNavError(fxmlPath, e);
        }
    }

    private static void showNavError(String fxmlPath, Exception e) {
        String screen = fxmlPath.contains("/") 
            ? fxmlPath.substring(fxmlPath.lastIndexOf("/") + 1).replace(".fxml", "")
            : fxmlPath;
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        javafx.application.Platform.runLater(() ->
            com.minimartpos.util.AlertUtil.showError(
                "Screen Load Error",
                "Could not load screen: " + screen + "\n\nReason: " + cause.getMessage()
            )
        );
    }

    /**
     * Navigate to screen and get the controller (useful for passing data).
     *
     * @param fxmlPath  Relative path under /fxml/
     * @return          The controller instance for the loaded FXML
     */
    public static <T> T navigateToWithController(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getFxmlUrl(fxmlPath));
        Parent root = loader.load();
        currentScene.setRoot(root);
        navigationStack.push(fxmlPath);
        logger.debug("Navigated to: {} (with controller)", fxmlPath);
        return loader.getController();
    }

    /**
     * Pop navigation stack and go back one screen.
     */
    public static void navigateBack() {
        if (navigationStack.size() > 1) {
            navigationStack.pop(); // remove current
            String previous = navigationStack.peek();
            try {
                Parent root = loadFXML(previous);
                currentScene.setRoot(root);
                logger.debug("Navigated back to: {}", previous);
            } catch (IOException e) {
                logger.error("Failed to navigate back to {}: {}", previous, e.getMessage(), e);
            }
        } else {
            logger.warn("navigateBack() called with no previous screen in stack.");
        }
    }

    /**
     * Opens a new modal window.
     *
     * @param fxmlPath   Relative path under /fxml/
     * @param title      Window title
     * @return           Stage of the opened modal
     */
    public static Stage openModal(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getFxmlUrl(fxmlPath));
            Parent root = loader.load();

            Stage modalStage = new Stage();
            modalStage.setTitle(title);
            modalStage.initModality(Modality.WINDOW_MODAL);
            modalStage.initOwner(primaryStage);

            Scene modalScene = new Scene(root);
            applyGlobalStylesheet(modalScene);
            modalStage.setScene(modalScene);
            modalStage.showAndWait();
            return modalStage;
        } catch (IOException e) {
            logger.error("Failed to open modal {}: {}", fxmlPath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Opens a modal and returns the controller (for data passing).
     */
    public static <T> T openModalWithController(String fxmlPath, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(getFxmlUrl(fxmlPath));
        Parent root = loader.load();

        Stage modalStage = new Stage();
        modalStage.setTitle(title);
        modalStage.initModality(Modality.WINDOW_MODAL);
        modalStage.initOwner(primaryStage);

        Scene modalScene = new Scene(root);
        applyGlobalStylesheet(modalScene);
        modalStage.setScene(modalScene);
        modalStage.showAndWait();

        return loader.getController();
    }

    /**
     * Clears the navigation stack (e.g. after logout).
     */
    public static void clearStack() {
        navigationStack.clear();
    }

    /**
     * Clears the FXML cache (e.g. after language change or theme switch).
     */
    public static void clearCache() {
        fxmlCache.clear();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private static Parent loadFXML(String fxmlPath) throws IOException {
        // Bypass cache for controllers with dynamic state; cache static screens
        URL url = getFxmlUrl(fxmlPath);
        FXMLLoader loader = new FXMLLoader(url);
        return loader.load();
    }

    private static URL getFxmlUrl(String fxmlPath) {
        String fullPath = AppConfig.FXML_BASE + fxmlPath;
        URL url = SceneManager.class.getResource(fullPath);
        if (url == null) {
            throw new IllegalArgumentException("FXML not found: " + fullPath);
        }
        return url;
    }

    private static void applyGlobalStylesheet(Scene scene) {
        URL css = SceneManager.class.getResource(AppConfig.CSS_BASE + "main.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            logger.warn("Global stylesheet not found: {}main.css", AppConfig.CSS_BASE);
        }
    }
}
