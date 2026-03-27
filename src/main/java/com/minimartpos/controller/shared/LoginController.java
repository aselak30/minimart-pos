package com.minimartpos.controller.shared;

import com.minimartpos.config.AppConfig;
import com.minimartpos.model.User;
import com.minimartpos.model.enums.Role;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.AuthService;
import com.minimartpos.util.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.ResourceBundle;

/**
 * Controller for the Login screen.
 * Handles authentication, lockout, CAPTCHA, and role-based routing.
 */
public class LoginController implements Initializable {

    private static final Logger logger = LogManager.getLogger(LoginController.class);

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     captchaField;
    @FXML private CheckBox      rememberMeCheck;
    @FXML private Button        loginBtn;
    @FXML private Button        togglePasswordBtn;
    @FXML private Label         errorLabel;
    @FXML private Label         versionLabel;
    @FXML private Label         captchaQuestion;
    @FXML private Label         lockoutCountdown;
    @FXML private VBox          captchaPane;
    @FXML private VBox          lockoutPane;

    private final AuthService authService = new AuthService();

    private int     failedAttempts  = 0;
    private int     captchaAnswer   = 0;
    private Timeline lockoutTimer;

    private static final String PREFS_NODE = "com/minimartpos";
    private static final String PREF_USER  = "rememberedUser";
    private TextField visiblePasswordField; // for show/hide toggle

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        versionLabel.setText("v" + AppConfig.APP_VERSION);

        passwordField.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> passwordField.requestFocus());

        // Load saved username
        try {
            java.util.prefs.Preferences prefs =
                java.util.prefs.Preferences.userRoot().node(PREFS_NODE);
            String saved = prefs.get(PREF_USER, "");
            if (!saved.isEmpty()) {
                usernameField.setText(saved);
                rememberMeCheck.setSelected(true);
                passwordField.requestFocus();
            }
        } catch (Exception ignored) {}
    }

    // ── Login Handler ─────────────────────────────────────────────────────────

    @FXML
    private void handleLogin() {
        clearError();

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Basic validation
        if (username.isEmpty()) { showError("Please enter your username."); return; }
        if (password.isEmpty()) { showError("Please enter your password.");  return; }

        // CAPTCHA check (after 3 failures)
        if (captchaPane.isVisible()) {
            try {
                int entered = Integer.parseInt(captchaField.getText().trim());
                if (entered != captchaAnswer) {
                    showError("Incorrect security answer. Please try again.");
                    generateCaptcha();
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Please enter a number for the security check.");
                return;
            }
        }

        loginBtn.setDisable(true);
        loginBtn.setText("Signing in…");

        // Run auth off the FX thread so UI stays responsive
        new Thread(() -> {
            try {
                AuthService.LoginResult result = authService.login(username, password);

                Platform.runLater(() -> {
                    loginBtn.setDisable(false);
                    loginBtn.setText("Sign In");
                    handleLoginResult(result);
                });

            } catch (Exception ex) {
                logger.error("Unexpected error during login", ex);
                Platform.runLater(() -> {
                    loginBtn.setDisable(false);
                    loginBtn.setText("Sign In");
                    showError("An unexpected error occurred. Please try again.");
                });
            }
        }).start();
    }

    private void handleLoginResult(AuthService.LoginResult result) {
        switch (result.getStatus()) {
            case SUCCESS -> {
                User user = result.getUser();
                SessionManager.login(user);
                logger.info("Login successful for user: {}", user.getUsername());
                failedAttempts = 0;
                saveRememberedUser(user.getUsername());
                routeToDashboard(user.getRole());
            }
            case INVALID_CREDENTIALS -> {
                failedAttempts++;
                logger.warn("Failed login attempt #{} for username: {}", failedAttempts, usernameField.getText());
                showError("Invalid username or password. (" + failedAttempts + "/" +
                          AppConfig.MAX_FAILED_LOGINS + " attempts)");
                passwordField.clear();

                if (failedAttempts >= AppConfig.CAPTCHA_AFTER) {
                    showCaptcha();
                }
                if (failedAttempts >= AppConfig.MAX_FAILED_LOGINS) {
                    showError("Too many failed attempts. Account locked for " +
                              AppConfig.LOCKOUT_DURATION_MIN + " minutes.");
                    startLockoutTimer();
                }
            }
            case ACCOUNT_LOCKED -> {
                showError("Account is currently locked.");
                if (result.getLockedUntil() != null) {
                    startLockoutTimerUntil(result.getLockedUntil());
                }
            }
            case ACCOUNT_DISABLED ->
                showError("This account has been disabled. Contact your administrator.");
            case DB_ERROR ->
                showError("Cannot connect to database. Please check your connection.");
        }
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    private void routeToDashboard(Role role) {
        SceneManager.clearStack();
        if (role == Role.ADMIN) {
            SceneManager.navigateTo("admin/AdminDashboard.fxml");
        } else {
            SceneManager.navigateTo("cashier/POSTerminal.fxml");
        }
    }

    // ── CAPTCHA ───────────────────────────────────────────────────────────────

    private void showCaptcha() {
        captchaPane.setVisible(true);
        captchaPane.setManaged(true);
        generateCaptcha();
    }

    private void generateCaptcha() {
        Random rng = new Random();
        int a = rng.nextInt(10) + 1;
        int b = rng.nextInt(10) + 1;
        captchaAnswer = a + b;
        captchaQuestion.setText(a + " + " + b + " = ?");
        captchaField.clear();
    }

    // ── Lockout Timer ─────────────────────────────────────────────────────────

    private void startLockoutTimer() {
        startLockoutTimerUntil(LocalDateTime.now().plusMinutes(AppConfig.LOCKOUT_DURATION_MIN));
    }

    private void startLockoutTimerUntil(LocalDateTime until) {
        lockoutPane.setVisible(true);
        lockoutPane.setManaged(true);
        loginBtn.setDisable(true);

        if (lockoutTimer != null) lockoutTimer.stop();

        lockoutTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long remaining = LocalDateTime.now().until(until, ChronoUnit.SECONDS);
            if (remaining <= 0) {
                lockoutTimer.stop();
                lockoutPane.setVisible(false);
                lockoutPane.setManaged(false);
                loginBtn.setDisable(false);
                failedAttempts = 0;
                clearError();
            } else {
                long mins = remaining / 60;
                long secs = remaining % 60;
                lockoutCountdown.setText(String.format("%02d:%02d", mins, secs));
            }
        }));
        lockoutTimer.setCycleCount(Timeline.INDEFINITE);
        lockoutTimer.play();
    }

    // ── Password Toggle ───────────────────────────────────────────────────────

    @FXML
    private void togglePasswordVisibility() {
        boolean isHidden = passwordField.isVisible();
        if (isHidden) {
            // Show password: create visible field if needed
            if (visiblePasswordField == null) {
                visiblePasswordField = new TextField();
                visiblePasswordField.setStyle(passwordField.getStyle());
                visiblePasswordField.setPrefWidth(passwordField.getPrefWidth());
                // Insert next to password field in its parent
                var parent = (javafx.scene.layout.Pane) passwordField.getParent();
                int idx = parent.getChildren().indexOf(passwordField);
                parent.getChildren().add(idx + 1, visiblePasswordField);
            }
            visiblePasswordField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            visiblePasswordField.setVisible(true);
            visiblePasswordField.setManaged(true);
            togglePasswordBtn.setText("🙈");
        } else {
            // Hide password
            passwordField.setText(
                visiblePasswordField != null ? visiblePasswordField.getText() : "");
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            if (visiblePasswordField != null) {
                visiblePasswordField.setVisible(false);
                visiblePasswordField.setManaged(false);
            }
            togglePasswordBtn.setText("👁");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveRememberedUser(String username) {
        try {
            java.util.prefs.Preferences prefs =
                java.util.prefs.Preferences.userRoot().node(PREFS_NODE);
            if (rememberMeCheck.isSelected()) {
                prefs.put(PREF_USER, username);
            } else {
                prefs.remove(PREF_USER);
            }
            prefs.flush();
        } catch (Exception ignored) {}
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
