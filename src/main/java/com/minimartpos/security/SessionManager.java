package com.minimartpos.security;

import com.minimartpos.config.AppConfig;
import com.minimartpos.model.User;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.model.enums.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Singleton managing the currently authenticated user session.
 *
 * Usage:
 *   SessionManager.login(user);
 *   SessionManager.getCurrentUser();
 *   SessionManager.hasPermission(Permission.VIEW_COST_PRICE);
 *   SessionManager.logout();
 */
public final class SessionManager {

    private static final Logger logger = LogManager.getLogger(SessionManager.class);

    private static User          currentUser;
    private static LocalDateTime loginTime;
    private static LocalDateTime lastActivity;
    private static String        machineId;

    private SessionManager() {}

    // ── Login / Logout ────────────────────────────────────────────────────────

    public static void login(User user) {
        currentUser   = user;
        loginTime     = LocalDateTime.now();
        lastActivity  = loginTime;
        logger.info("User logged in: {} [{}] from machine: {}",
                    user.getUsername(), user.getRole(), machineId);
    }

    public static void logout() {
        if (currentUser != null) {
            logger.info("User logged out: {} (session duration: {} min)",
                        currentUser.getUsername(), getSessionDurationMinutes());
        }
        currentUser  = null;
        loginTime    = null;
        lastActivity = null;
    }

    // ── Session State ─────────────────────────────────────────────────────────

    public static boolean isLoggedIn() {
        return currentUser != null && !isSessionExpired();
    }

    public static boolean isSessionExpired() {
        if (lastActivity == null) return true;
        int timeout = currentUser != null
                      ? currentUser.getSessionTimeoutMinutes()
                      : AppConfig.SESSION_TIMEOUT_MIN;
        return LocalDateTime.now().isAfter(lastActivity.plusMinutes(timeout));
    }

    /** Call this on any user activity to reset the inactivity timer. */
    public static void touch() {
        lastActivity = LocalDateTime.now();
    }

    public static long getSessionDurationMinutes() {
        if (loginTime == null) return 0;
        return java.time.Duration.between(loginTime, LocalDateTime.now()).toMinutes();
    }

    // ── Permission Checks ─────────────────────────────────────────────────────

    /**
     * Returns true if the current user has the given permission.
     * Admins automatically have ALL permissions.
     */
    public static boolean hasPermission(Permission permission) {
        if (currentUser == null) return false;
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.SUPER_ADMIN) return true;
        return currentUser.getPermissions().contains(permission);
    }

    public static boolean hasAnyPermission(Permission... permissions) {
        for (Permission p : permissions) {
            if (hasPermission(p)) return true;
        }
        return false;
    }

    public static boolean hasAllPermissions(Permission... permissions) {
        for (Permission p : permissions) {
            if (!hasPermission(p)) return false;
        }
        return true;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static User          getCurrentUser()  { return currentUser; }
    public static LocalDateTime getLoginTime()    { return loginTime; }
    public static LocalDateTime getLastActivity() { return lastActivity; }
    public static String        getMachineId()    { return machineId; }

    public static void setMachineId(String id) {
        machineId = id;
    }

    public static Set<Permission> getCurrentPermissions() {
        if (currentUser == null) return Set.of();
        return currentUser.getPermissions();
    }
}
