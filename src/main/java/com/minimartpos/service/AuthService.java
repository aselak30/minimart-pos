package com.minimartpos.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.minimartpos.config.AppConfig;
import com.minimartpos.model.User;
import com.minimartpos.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Handles authentication, password management, and account lockout logic.
 */
public class AuthService {

    private static final Logger logger = LogManager.getLogger(AuthService.class);

    private final UserRepository userRepository = new UserRepository();

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user by username and password.
     * Handles: invalid credentials, account lockout, account disabled.
     */
    public LoginResult login(String username, String rawPassword) {
        try {
            Optional<User> optUser = userRepository.findByUsername(username);

            if (optUser.isEmpty()) {
                logger.warn("Login attempt for unknown username: {}", username);
                return LoginResult.invalidCredentials();
            }

            User user = optUser.get();

            if (!user.isActive()) {
                return LoginResult.accountDisabled();
            }

            if (user.isLocked()) {
                return LoginResult.accountLocked(user.getLockedUntil());
            }

            boolean passwordMatch = BCrypt.verifyer()
                    .verify(rawPassword.toCharArray(), user.getPasswordHash())
                    .verified;

            if (!passwordMatch) {
                handleFailedAttempt(user);
                return LoginResult.invalidCredentials();
            }

            // Success — reset failed attempts and update last login
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLogin(LocalDateTime.now());
            userRepository.updateLoginSuccess(user);

            // Load full permissions
            userRepository.loadPermissions(user);

            return LoginResult.success(user);

        } catch (Exception e) {
            logger.error("Database error during login for {}: {}", username, e.getMessage(), e);
            return LoginResult.dbError();
        }
    }

    // ── Password Management ───────────────────────────────────────────────────

    /**
     * Hashes a plain-text password using BCrypt.
     */
    public String hashPassword(String plainPassword) {
        return BCrypt.withDefaults()
                     .hashToString(AppConfig.BCRYPT_STRENGTH, plainPassword.toCharArray());
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     */
    public boolean verifyPassword(String plainPassword, String hash) {
        return BCrypt.verifyer()
                     .verify(plainPassword.toCharArray(), hash)
                     .verified;
    }

    /**
     * Changes a user's password. Validates old password first.
     * Returns true if successful.
     */
    public boolean changePassword(User user, String oldPassword, String newPassword) {
        if (!verifyPassword(oldPassword, user.getPasswordHash())) {
            return false;
        }
        String newHash = hashPassword(newPassword);
        user.setPasswordHash(newHash);
        userRepository.updatePassword(user.getId(), newHash);
        logger.info("Password changed for user: {}", user.getUsername());
        return true;
    }

    /**
     * Resets a user's password (admin action — no old password needed).
     * Returns the new temporary password.
     */
    public String resetPassword(int userId) {
        String tempPassword = generateTempPassword();
        String hash = hashPassword(tempPassword);
        userRepository.updatePassword(userId, hash);
        logger.info("Password reset for userId: {}", userId);
        return tempPassword;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns a description of password strength: WEAK, FAIR, STRONG, VERY_STRONG.
     */
    public String getPasswordStrength(String password) {
        if (password == null || password.length() < 6) return "WEAK";
        int score = 0;
        if (password.length() >= 8)  score++;
        if (password.length() >= 12) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*\\d.*"))   score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;
        return switch (score) {
            case 0, 1, 2 -> "WEAK";
            case 3       -> "FAIR";
            case 4, 5    -> "STRONG";
            default      -> "VERY_STRONG";
        };
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void handleFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= AppConfig.MAX_FAILED_LOGINS) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(AppConfig.LOCKOUT_DURATION_MIN);
            user.setLockedUntil(lockUntil);
            logger.warn("Account locked for user: {} until {}", user.getUsername(), lockUntil);
        }

        userRepository.updateFailedLogin(user);
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
        StringBuilder sb = new StringBuilder();
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ── LoginResult inner class ───────────────────────────────────────────────

    public static class LoginResult {

        public enum Status { SUCCESS, INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED, DB_ERROR }

        private final Status        status;
        private final User          user;
        private final LocalDateTime lockedUntil;

        private LoginResult(Status status, User user, LocalDateTime lockedUntil) {
            this.status      = status;
            this.user        = user;
            this.lockedUntil = lockedUntil;
        }

        public static LoginResult success(User user) {
            return new LoginResult(Status.SUCCESS, user, null);
        }
        public static LoginResult invalidCredentials() {
            return new LoginResult(Status.INVALID_CREDENTIALS, null, null);
        }
        public static LoginResult accountLocked(LocalDateTime until) {
            return new LoginResult(Status.ACCOUNT_LOCKED, null, until);
        }
        public static LoginResult accountDisabled() {
            return new LoginResult(Status.ACCOUNT_DISABLED, null, null);
        }
        public static LoginResult dbError() {
            return new LoginResult(Status.DB_ERROR, null, null);
        }

        public Status        getStatus()      { return status; }
        public User          getUser()        { return user; }
        public LocalDateTime getLockedUntil() { return lockedUntil; }
    }
}
