package com.minimartpos.service;

import com.minimartpos.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.*;

/**
 * Handles all stock quantity operations and records adjustments in
 * stock_adjustments table.
 */
public class StockService {

    private static final Logger logger = LogManager.getLogger(StockService.class);

    private static final String SQL_GET_STOCK = "SELECT stock_quantity FROM products WHERE id=? FOR UPDATE";

    private static final String SQL_ADJUST_STOCK = "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at=NOW() WHERE id=?";

    private static final String SQL_INSERT_ADJUSTMENT = "INSERT INTO stock_adjustments (product_id, adjustment, reason, reference_id, "
            +
            "old_quantity, new_quantity, adjusted_by) VALUES (?,?,?,?,?,?,?)";

    /**
     * Deducts stock after a successful sale.
     * Uses SELECT FOR UPDATE to prevent race conditions on concurrent machines.
     */
    public boolean deductStock(int productId, BigDecimal quantity, int billId, int userId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal current = getCurrentStock(conn, productId);
                if (current.compareTo(quantity) < 0) {
                    conn.rollback();
                    logger.warn("Stock deduction failed: product={} has {} but need {}",
                            productId, current, quantity);
                    return false;
                }
                adjustStock(conn, productId, quantity.negate());
                recordAdjustment(conn, productId, quantity.negate(), "SALE", billId,
                        current, current.subtract(quantity), userId);
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
    public boolean addStock(int productId, BigDecimal quantity, int referenceId, int userId, String reason) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal current = getCurrentStock(conn, productId);
                adjustStock(conn, productId, quantity);
                recordAdjustment(conn, productId, quantity, reason, referenceId,
                        current, current.add(quantity), userId);
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

    public boolean addDamagedStock(int productId, BigDecimal quantity, int referenceId, int userId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // We don't change products.stock_quantity for damaged items,
                // but we increment products.damaged_quantity if we want to track it.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET damaged_quantity = damaged_quantity + ?, updated_at=NOW() WHERE id=?")) {
                    ps.setBigDecimal(1, quantity);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }

                // Get current stock for recording purposes (though it didn't change)
                BigDecimal current = getCurrentStock(conn, productId);

                recordAdjustment(conn, productId, quantity, "DAMAGE", referenceId,
                        current, current, userId); // new quantity is same as old for main stock

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("addDamagedStock error product {}: {}", productId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Manual stock set by admin/cashier with permission.
     */
    public boolean manualAdjust(int productId, BigDecimal newQuantity, int userId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal current = getCurrentStock(conn, productId);
                BigDecimal delta = newQuantity.subtract(current);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET stock_quantity=?, updated_at=NOW() WHERE id=?")) {
                    ps.setBigDecimal(1, newQuantity);
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

    private BigDecimal getCurrentStock(Connection conn, int productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_GET_STOCK)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("stock_quantity") : BigDecimal.ZERO;
            }
        }
    }

    private void adjustStock(Connection conn, int productId, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_ADJUST_STOCK)) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    private void recordAdjustment(Connection conn, int productId, BigDecimal adjustment,
            String reason, int refId, BigDecimal oldQty, BigDecimal newQty,
            int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ADJUSTMENT)) {
            ps.setInt(1, productId);
            ps.setBigDecimal(2, adjustment);
            ps.setString(3, reason);
            ps.setInt(4, refId);
            ps.setBigDecimal(5, oldQty);
            ps.setBigDecimal(6, newQty);
            ps.setInt(7, userId);
            ps.executeUpdate();
        }
    }
}
