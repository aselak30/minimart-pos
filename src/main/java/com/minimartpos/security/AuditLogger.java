package com.minimartpos.security;

import com.minimartpos.service.AuditService;

/**
 * Static convenience wrapper so callers don't have to instantiate AuditService.
 *
 * Usage:
 *   AuditLogger.log("LOGIN_SUCCESS", "users", userId, null, null);
 */
public final class AuditLogger {

    private static final AuditService service = new AuditService();

    private AuditLogger() {}

    public static void log(String action, String entityType, int entityId,
                           String oldValue, String newValue) {
        service.log(action, entityType, entityId, oldValue, newValue);
    }

    public static void logRaw(int userId, String username, String action,
                               String entityType, int entityId,
                               String oldValue, String newValue) {
        service.logRaw(userId, username, action, entityType, entityId, oldValue, newValue);
    }
}
