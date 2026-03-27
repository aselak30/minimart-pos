package com.minimartpos.service;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.security.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Records critical user actions to the audit_log table.
 * All writes are fire-and-forget (errors are logged, not thrown).
 */
public class AuditService {

    private static final Logger logger = LogManager.getLogger(AuditService.class);

    private static final String SQL_INSERT =
        "INSERT INTO audit_log (user_id, username, action, entity_type, entity_id, " +
        "old_value, new_value) VALUES (?,?,?,?,?,?,?)";

    /**
     * Records an audit event for the currently logged-in user.
     *
     * @param action      Short action code, e.g. "LOGIN", "BILL_VOID", "PRODUCT_EDIT"
     * @param entityType  Table/entity name, e.g. "bills", "products"
     * @param entityId    Primary key of the affected row (0 if not applicable)
     * @param oldValue    Previous value (JSON or plain text) — nullable
     * @param newValue    New value (JSON or plain text) — nullable
     */
    public void log(String action, String entityType, int entityId,
                    String oldValue, String newValue) {
        int    userId   = 0;
        String username = "system";
        if (SessionManager.isLoggedIn()) {
            userId   = SessionManager.getCurrentUser().getId();
            username = SessionManager.getCurrentUser().getUsername();
        }
        logRaw(userId, username, action, entityType, entityId, oldValue, newValue);
    }

    /** Logs without requiring an active session (e.g. login events). */
    public void logRaw(int userId, String username, String action,
                       String entityType, int entityId,
                       String oldValue, String newValue) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            ps.setInt(1,    userId);
            ps.setString(2, username);
            ps.setString(3, action);
            ps.setString(4, entityType);
            ps.setInt(5,    entityId);
            ps.setString(6, oldValue);
            ps.setString(7, newValue);
            ps.executeUpdate();
        } catch (Exception e) {
            // Audit log failure must never break application flow
            logger.warn("Audit log write failed for action={}: {}", action, e.getMessage());
        }
    }
}
