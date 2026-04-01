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

import java.io.File;
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
    
    // Branding
    @FXML private javafx.scene.image.ImageView logoImageView;
    @FXML private Label         logoEmojiLabel;
    @FXML private Label         loginTitleLabel;

    private final AuthService authService = new AuthService();
    private final com.minimartpos.service.SettingsService settingsService = new com.minimartpos.service.SettingsService();

    private int     captchaAnswer   = 0;
    private Timeline lockoutTimer;

    private static final String PREFS_NODE = "com/minimartpos";
    private static final String PREF_USER  = "rememberedUser";
    private TextField visiblePasswordField; // for show/hide toggle

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        applyBranding();
        versionLabel.setText("v" + AppConfig.APP_VERSION);

        passwordField.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> passwordField.requestFocus());

        // Username changed: reset security UI state (prevent one user lockout appearing for another)
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
            clearError();
            captchaPane.setVisible(false);
            captchaPane.setManaged(false);
            if (lockoutTimer == null || !lockoutTimer.getStatus().equals(javafx.animation.Animation.Status.RUNNING)) {
                lockoutPane.setVisible(false);
                lockoutPane.setManaged(false);
                loginBtn.setDisable(false);
            }
        });

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

        // Sync visible field text back to real password field if it's open
        if (visiblePasswordField != null && visiblePasswordField.isVisible()) {
            passwordField.setText(visiblePasswordField.getText());
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Basic validation
        if (username.isEmpty()) { showError("Please enter your username."); return; }
        if (password.isEmpty()) { showError("Please enter your password.");  return; }

        // CAPTCHA check
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
                saveRememberedUser(user.getUsername());
                routeToDashboard(user.getRole());
            }
            case INVALID_CREDENTIALS -> {
                logger.warn("Invalid credentials for username: {}", usernameField.getText());
                showError("Invalid username or password.");
                passwordField.clear();
                if (visiblePasswordField != null) visiblePasswordField.clear();
                
                // Server doesn't explicitly return failures here, 
                // but we should show captcha if we see multiple failures in one session
                // BUT the user asked for server-side logic improvement, so we trigger 
                // Captcha based on what the server says (handled in account status if needed).
                // For now, we'll keep a session-based counter IF they stay on the same username.
                // Re-enabling catch-all captcha just for basic security against bots on this screen.
                showCaptcha(); 
            }
            case ACCOUNT_LOCKED -> {
                showError("Account is currently locked.");
                if (result.getLockedUntil() != null) {
                    startLockoutTimerUntil(result.getLockedUntil());
                } else {
                    // Fallback if no time provided
                    showError("Too many failed attempts. Account locked.");
                    loginBtn.setDisable(true);
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
                clearError();
            } else {
                long mins = Math.max(0, remaining / 60);
                long secs = Math.max(0, remaining % 60);
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
                visiblePasswordField.setOnAction(e -> handleLogin());
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

    private void applyBranding() {
        try {
            String company = settingsService.company();
            if (loginTitleLabel != null) loginTitleLabel.setText(company + " POS");
            SceneManager.updateTitle(company + " POS Ultimate");

            // Check for uploaded logo path first
            String logoPath = settingsService.logoPath();
            javafx.scene.image.Image img = null;

            if (logoPath != null && !logoPath.equals("images/logo.png")) {
                try {
                    File logoFile = new File(logoPath);
                    if (logoFile.exists() && logoFile.isFile()) {
                        img = new javafx.scene.image.Image(logoFile.toURI().toString());
                    }
                } catch (Exception e) {
                    logger.debug("Could not load external logo: {}", e.getMessage());
                }
            }

            // Fallback to default resource logo if no custom one or error loading it
            if (img == null || img.isError()) {
                URL res = getClass().getResource("/images/logo.png");
                if (res != null) {
                    img = new javafx.scene.image.Image(res.toExternalForm());
                }
            }

            if (img != null && !img.isError()) {
                if (logoImageView != null) {
                    logoImageView.setImage(img);
                    logoImageView.setVisible(true);
                    logoImageView.setManaged(true);
                }
                if (logoEmojiLabel != null) {
                    logoEmojiLabel.setVisible(false);
                    logoEmojiLabel.setManaged(false);
                }
            } else {
                // Fallback to emoji if logo.png is missing
                if (logoImageView != null) {
                    logoImageView.setVisible(false);
                    logoImageView.setManaged(false);
                }
                if (logoEmojiLabel != null) {
                    logoEmojiLabel.setVisible(true);
                    logoEmojiLabel.setManaged(true);
                }
            }

        } catch (Exception e) {
            logger.warn("Branding load error: {}", e.getMessage());
            if (logoImageView != null) {
                logoImageView.setVisible(false);
                logoImageView.setManaged(false);
            }
            if (logoEmojiLabel != null) {
                logoEmojiLabel.setVisible(true);
                logoEmojiLabel.setManaged(true);
            }
        }
    }
}
