package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Setting;
import com.minimartpos.security.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.*;

/**
 * Data access for the settings table.
 */
public class SettingsRepository {

    private static final Logger logger = LogManager.getLogger(SettingsRepository.class);

    public Map<String, String> loadAll() {
        Map<String, String> map = new LinkedHashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT setting_key, setting_value FROM settings ORDER BY setting_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) map.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) {
            logger.error("loadAll settings error: {}", e.getMessage(), e);
        }
        return map;
    }

    public String get(String key, String defaultValue) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT setting_value FROM settings WHERE setting_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.error("get setting '{}' error: {}", key, e.getMessage(), e);
        }
        return defaultValue;
    }

    public void set(String key, String value) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO settings (setting_key, setting_value, updated_by) VALUES (?,?,?) " +
                 "ON DUPLICATE KEY UPDATE setting_value=?, updated_by=?")) {
            int userId = SessionManager.isLoggedIn() ? SessionManager.getCurrentUser().getId() : 0;
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setInt(3, userId);
            ps.setString(4, value);
            ps.setInt(5, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("set setting '{}' error: {}", key, e.getMessage(), e);
        }
    }

    public void setAll(Map<String, String> settings) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO settings (setting_key, setting_value, updated_by) VALUES (?,?,?) " +
                 "ON DUPLICATE KEY UPDATE setting_value=?, updated_by=?")) {
                int userId = SessionManager.isLoggedIn() ? SessionManager.getCurrentUser().getId() : 0;
                for (Map.Entry<String, String> e : settings.entrySet()) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, e.getValue());
                    ps.setInt(3, userId);
                    ps.setString(4, e.getValue());
                    ps.setInt(5, userId);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback(); throw e;
            } finally { conn.setAutoCommit(true); }
        } catch (SQLException e) {
            logger.error("setAll settings error: {}", e.getMessage(), e);
        }
    }
}
