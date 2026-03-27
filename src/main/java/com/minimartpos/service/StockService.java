package com.minimartpos.service;

import com.minimartpos.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

/**
 * Handles all stock quantity operations and records adjustments in stock_adjustments table.
 */
public class StockService {

    private static final Logger logger = LogManager.getLogger(StockService.class);

    private static final String SQL_GET_STOCK =
        "SELECT stock_quantity FROM products WHERE id=? FOR UPDATE";

    private static final String SQL_ADJUST_STOCK =
        "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at=NOW() WHERE id=?";

    private static final String SQL_INSERT_ADJUSTMENT =
        "INSERT INTO stock_adjustments (product_id, adjustment, reason, reference_id, " +
        "old_quantity, new_quantity, adjusted_by) VALUES (?,?,?,?,?,?,?)";

    /**
     * Deducts stock after a successful sale.
     * Uses SELECT FOR UPDATE to prevent race conditions on concurrent machines.
     */
    public boolean deductStock(int productId, int quantity, int billId, int userId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int current = getCurrentStock(conn, productId);
                if (current < quantity) {
                    conn.rollback();
                    logger.warn("Stock deduction failed: product={} has {} but need {}",
                                productId, current, quantity);
                    return false;
                }
                adjustStock(conn, productId, -quantity);
                recordAdjustment(conn, productId, -quantity, "SALE", billId,
                                 current, current - quantity, userId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("deductStock error product {}: {}", productId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Adds stock back (e.g. on void or return).
     */
    public boolean addStock(int productId, int quantity, int referenceId, int userId, String reason) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int current = getCurrentStock(conn, productId);
                adjustStock(conn, productId, quantity);
                recordAdjustment(conn, productId, quantity, reason, referenceId,
                                 current, current + quantity, userId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("addStock error product {}: {}", productId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Manual stock set by admin/cashier with permission.
     */
    public boolean manualAdjust(int productId, int newQuantity, int userId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int current = getCurrentStock(conn, productId);
                int delta   = newQuantity - current;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET stock_quantity=?, updated_at=NOW() WHERE id=?")) {
                    ps.setInt(1, newQuantity);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }
                recordAdjustment(conn, productId, delta, "ADJUSTMENT", 0,
                                 current, newQuantity, userId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("manualAdjust error: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getCurrentStock(Connection conn, int productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_GET_STOCK)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("stock_quantity") : 0;
            }
        }
    }

    private void adjustStock(Connection conn, int productId, int delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_ADJUST_STOCK)) {
            ps.setInt(1, delta);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    private void recordAdjustment(Connection conn, int productId, int adjustment,
                                   String reason, int refId, int oldQty, int newQty,
                                   int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ADJUSTMENT)) {
            ps.setInt(1,    productId);
            ps.setInt(2,    adjustment);
            ps.setString(3, reason);
            ps.setInt(4,    refId);
            ps.setInt(5,    oldQty);
            ps.setInt(6,    newQty);
            ps.setInt(7,    userId);
            ps.executeUpdate();
        }
    }
}
