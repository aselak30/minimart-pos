package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.User;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.model.enums.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Data access object for the users table.
 * All queries use parameterized statements to prevent SQL injection.
 */
public class UserRepository {

    private static final Logger logger = LogManager.getLogger(UserRepository.class);

    // ── Queries ───────────────────────────────────────────────────────────────

    private static final String SQL_FIND_BY_USERNAME =
        "SELECT * FROM users WHERE username = ? LIMIT 1";

    private static final String SQL_FIND_BY_ID =
        "SELECT * FROM users WHERE id = ? LIMIT 1";

    private static final String SQL_FIND_ALL =
        "SELECT * FROM users ORDER BY full_name";

    private static final String SQL_INSERT =
        "INSERT INTO users (username, password_hash, full_name, role, email, phone, active, " +
        "session_timeout_minutes, cash_limit, daily_sales_target, theme_preference) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
        "UPDATE users SET full_name=?, role=?, email=?, phone=?, active=?, " +
        "session_timeout_minutes=?, cash_limit=?, daily_sales_target=?, " +
        "theme_preference=?, updated_at=NOW() WHERE id=?";

    private static final String SQL_UPDATE_PASSWORD =
        "UPDATE users SET password_hash=?, updated_at=NOW() WHERE id=?";

    private static final String SQL_UPDATE_LOGIN_SUCCESS =
        "UPDATE users SET last_login=?, failed_login_attempts=0, locked_until=NULL WHERE id=?";

    private static final String SQL_UPDATE_FAILED_LOGIN =
        "UPDATE users SET failed_login_attempts=?, locked_until=? WHERE id=?";

    private static final String SQL_UPDATE_THEME =
        "UPDATE users SET theme_preference=?, last_theme_change=NOW() WHERE id=?";

    private static final String SQL_DELETE_PERMISSIONS =
        "DELETE FROM user_permissions WHERE user_id=?";

    private static final String SQL_INSERT_PERMISSION =
        "INSERT INTO user_permissions (user_id, permission, granted_by) VALUES (?, ?, ?)";

    private static final String SQL_LOAD_PERMISSIONS =
        "SELECT permission FROM user_permissions WHERE user_id=?";

    // ── Public Methods ────────────────────────────────────────────────────────

    public Optional<User> findByUsername(String username) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_USERNAME)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findByUsername error: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<User> findById(int id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("findById error: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) users.add(mapRow(rs));
        } catch (SQLException e) {
            logger.error("findAll error: {}", e.getMessage(), e);
        }
        return users;
    }

    public int insert(User user) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getEmail());
            ps.setString(6, user.getPhone());
            ps.setBoolean(7, user.isActive());
            ps.setInt(8,     user.getSessionTimeoutMinutes());
            ps.setBigDecimal(9,  user.getCashLimit());
            ps.setBigDecimal(10, user.getDailySalesTarget());
            ps.setString(11, user.getThemePreference());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    user.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("insert user error: {}", e.getMessage(), e);
        }
        return -1;
    }

    public boolean update(User user) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1,  user.getFullName());
            ps.setString(2,  user.getRole().name());
            ps.setString(3,  user.getEmail());
            ps.setString(4,  user.getPhone());
            ps.setBoolean(5, user.isActive());
            ps.setInt(6,     user.getSessionTimeoutMinutes());
            ps.setBigDecimal(7, user.getCashLimit());
            ps.setBigDecimal(8, user.getDailySalesTarget());
            ps.setString(9,  user.getThemePreference());
            ps.setInt(10,    user.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("update user error: {}", e.getMessage(), e);
        }
        return false;
    }

    public void updatePassword(int userId, String newHash) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_PASSWORD)) {
            ps.setString(1, newHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("updatePassword error: {}", e.getMessage(), e);
        }
    }

    public void updateLoginSuccess(User user) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_LOGIN_SUCCESS)) {
            ps.setTimestamp(1, Timestamp.valueOf(user.getLastLogin()));
            ps.setInt(2, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("updateLoginSuccess error: {}", e.getMessage(), e);
        }
    }

    public void updateFailedLogin(User user) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_FAILED_LOGIN)) {
            ps.setInt(1, user.getFailedLoginAttempts());
            ps.setTimestamp(2, user.getLockedUntil() != null
                               ? Timestamp.valueOf(user.getLockedUntil()) : null);
            ps.setInt(3, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("updateFailedLogin error: {}", e.getMessage(), e);
        }
    }

    public void updateTheme(int userId, String theme) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_THEME)) {
            ps.setString(1, theme);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("updateTheme error: {}", e.getMessage(), e);
        }
    }

    // ── Permission Management ─────────────────────────────────────────────────

    public void loadPermissions(User user) {
        Set<Permission> perms = EnumSet.noneOf(Permission.class);
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LOAD_PERMISSIONS)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        perms.add(Permission.valueOf(rs.getString("permission")));
                    } catch (IllegalArgumentException e) {
                        logger.warn("Unknown permission in DB: {}", rs.getString("permission"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("loadPermissions error: {}", e.getMessage(), e);
        }
        user.setPermissions(perms);
    }

    public void savePermissions(int userId, Set<Permission> permissions, int grantedBy) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete existing
                try (PreparedStatement del = conn.prepareStatement(SQL_DELETE_PERMISSIONS)) {
                    del.setInt(1, userId);
                    del.executeUpdate();
                }
                // Insert new
                try (PreparedStatement ins = conn.prepareStatement(SQL_INSERT_PERMISSION)) {
                    for (Permission p : permissions) {
                        ins.setInt(1, userId);
                        ins.setString(2, p.name());
                        ins.setInt(3, grantedBy);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                conn.commit();
                logger.info("Saved {} permissions for userId={}", permissions.size(), userId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("savePermissions error: {}", e.getMessage(), e);
        }
    }


    // ── Delete ────────────────────────────────────────────────────────────────

    public boolean delete(int userId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete permissions first (FK constraint)
                try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_PERMISSIONS)) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                // Delete user
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id=?")) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                conn.commit();
                logger.info("User deleted: id={}", userId);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                logger.error("delete user error: {}", e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("delete user connection error: {}", e.getMessage(), e);
        }
        return false;
    }

    // ── Row Mapper ────────────────────────────────────────────────────────────

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(Role.valueOf(rs.getString("role")));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setActive(rs.getBoolean("active"));
        u.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        u.setSessionTimeoutMinutes(rs.getInt("session_timeout_minutes"));

        // Cash limit fields (safe fallback if column not yet migrated)
        try {
            java.math.BigDecimal cl = rs.getBigDecimal("cash_limit");
            u.setCashLimit(cl != null ? cl : java.math.BigDecimal.ZERO);
            java.math.BigDecimal dst = rs.getBigDecimal("daily_sales_target");
            u.setDailySalesTarget(dst != null ? dst : java.math.BigDecimal.ZERO);
        } catch (SQLException ignored) {
            // Column may not exist on older installs — safe default
        }

        try {
            u.setThemePreference(rs.getString("theme_preference"));
            Timestamp themeTime = rs.getTimestamp("last_theme_change");
            if (themeTime != null) u.setLastThemeChange(themeTime.toLocalDateTime());
        } catch (SQLException ignored) {
            // Theme fields may not exist yet
        }

        Timestamp locked = rs.getTimestamp("locked_until");
        if (locked != null) u.setLockedUntil(locked.toLocalDateTime());

        Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) u.setLastLogin(lastLogin.toLocalDateTime());

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) u.setCreatedAt(created.toLocalDateTime());

        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) u.setUpdatedAt(updated.toLocalDateTime());

        return u;
    }
}
