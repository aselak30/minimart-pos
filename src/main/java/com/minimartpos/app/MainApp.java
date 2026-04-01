package com.minimartpos.app;

import com.minimartpos.config.AppConfig;
import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.util.SceneManager;
import com.minimartpos.security.SessionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main JavaFX Application class for MiniMart POS Ultimate.
 * Handles application lifecycle: startup, scene management, and shutdown.
 */
public class MainApp extends Application {

    private static final Logger logger = LogManager.getLogger(MainApp.class);

    @Override
    public void init() throws Exception {
        logger.info("=== MiniMart POS Ultimate Starting ===");
        logger.info("Version: {}", AppConfig.APP_VERSION);

        // Initialize database connection pool
        try {
            DatabaseConfig.initialize();
            logger.info("Database connection pool initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize database: {}", e.getMessage(), e);
            // Allow app to start; show DB config screen instead
        }

        // Start multi-machine sync (non-blocking, non-fatal)
        if (DatabaseConfig.isConnected()) {
            try {
                com.minimartpos.network.SyncManager.getInstance().start();
                logger.info("SyncManager started.");
            } catch (Exception e) {
                logger.warn("SyncManager failed to start (single-machine mode): {}", e.getMessage());
            }
        }

        // Start offline queue sync monitor (always runs — watches for reconnection)
        try {
            com.minimartpos.network.OfflineSync.getInstance().start();
            logger.info("OfflineSync started. Pending offline bills: {}",
                com.minimartpos.network.OfflineSync.getInstance().pendingCount());
        } catch (Exception e) {
            logger.warn("OfflineSync failed to start: {}", e.getMessage());
        }

        // Start auto daily backup scheduler
        try {
            com.minimartpos.service.AutoBackupScheduler.getInstance().start();
            logger.info("AutoBackupScheduler started. Next: {}",
                com.minimartpos.service.AutoBackupScheduler.getInstance().getNextBackupTime());
        } catch (Exception e) {
            logger.warn("BackupScheduler failed to start: {}", e.getMessage());
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Starting JavaFX stage...");

        // Initialize scene manager with primary stage
        SceneManager.initialize(primaryStage);

        primaryStage.setTitle(AppConfig.APP_TITLE);
        primaryStage.setMinWidth(AppConfig.MIN_WIDTH);
        primaryStage.setMinHeight(AppConfig.MIN_HEIGHT);

        // Handle window close request gracefully
        primaryStage.setOnCloseRequest(event -> {
            logger.info("Close requested — shutting down.");
            event.consume();
            shutdown();
        });

        // Navigate to login screen
        if (DatabaseConfig.isConnected()) {
            // Update title from settings
            try {
                com.minimartpos.service.SettingsService settings = new com.minimartpos.service.SettingsService();
                SceneManager.updateTitle(settings.company() + " POS Ultimate");
            } catch (Exception e) {
                logger.debug("Failed to load title from settings: {}", e.getMessage());
            }
            SceneManager.navigateTo("shared/Login.fxml");
        } else {
            SceneManager.navigateTo("shared/DatabaseSetup.fxml");
        }

        primaryStage.show();

        // Session inactivity auto-logout (checks every 60s)
        javafx.animation.Timeline sessionWatcher = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(60), e -> {
                if (SessionManager.isLoggedIn() && SessionManager.isSessionExpired()) {
                    logger.info("Session expired — auto-logout.");
                    SessionManager.logout();
                    SceneManager.clearStack();
                    SceneManager.navigateTo("shared/Login.fxml");
                }
            })
        );
        sessionWatcher.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sessionWatcher.play();

        logger.info("Application started successfully.");
    }

    @Override
    public void stop() throws Exception {
        logger.info("JavaFX application stopping...");
        try {
            com.minimartpos.network.SyncManager.getInstance().stop();
        } catch (Exception e) {
            logger.debug("SyncManager stop: {}", e.getMessage());
        }
        try {
            com.minimartpos.network.OfflineSync.getInstance().stop();
        } catch (Exception e) {
            logger.debug("OfflineSync stop: {}", e.getMessage());
        }
        try {
            com.minimartpos.service.AutoBackupScheduler.getInstance().stop();
        } catch (Exception e) {
            logger.debug("BackupScheduler stop: {}", e.getMessage());
        }
        DatabaseConfig.shutdown();
        logger.info("=== MiniMart POS Ultimate Stopped ===");
    }

    /**
     * Graceful shutdown with optional confirmation dialog.
     */
    public static void shutdown() {
        logger.info("Shutting down application...");
        Platform.exit();
    }

    /**
     * Entry point when running directly (not via Launcher).
     * JavaFX requires that Application subclasses NOT be the class
     * with main() when packaged as a fat JAR — use Launcher instead.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
