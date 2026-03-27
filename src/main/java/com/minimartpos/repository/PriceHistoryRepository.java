package com.minimartpos.repository;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.PriceHistoryEntry;
import com.minimartpos.security.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the product_price_history table.
 */
public class PriceHistoryRepository {

    private static final Logger logger = LogManager.getLogger(PriceHistoryRepository.class);

    private static final String SQL_INSERT =
        "INSERT INTO product_price_history " +
        "(product_id, old_price, new_price, old_cost, new_cost, changed_by, reason) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_FIND_BY_PRODUCT =
        "SELECT h.*, u.full_name AS changed_by_name " +
        "FROM product_price_history h " +
        "LEFT JOIN users u ON h.changed_by = u.id " +
        "WHERE h.product_id = ? " +
        "ORDER BY h.changed_at DESC " +
        "LIMIT 100";

    /**
     * Records a price change. Call this whenever a product's unit_price or
     * cost_price changes during a save operation.
     *
     * @param productId  The product being changed
     * @param oldPrice   Previous selling price
     * @param newPrice   New selling price
     * @param oldCost    Previous cost price (nullable)
     * @param newCost    New cost price (nullable)
     * @param reason     Optional reason string
     */
    public void record(int productId,
                       java.math.BigDecimal oldPrice, java.math.BigDecimal newPrice,
                       java.math.BigDecimal oldCost,  java.math.BigDecimal newCost,
                       String reason) {
        int userId = SessionManager.isLoggedIn()
            ? SessionManager.getCurrentUser().getId() : 0;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            ps.setInt(1,     productId);
            ps.setBigDecimal(2, oldPrice);
            ps.setBigDecimal(3, newPrice);
            ps.setBigDecimal(4, oldCost);
            ps.setBigDecimal(5, newCost);
            ps.setInt(6,     userId);
            ps.setString(7, reason);
            ps.executeUpdate();
            logger.debug("Price history recorded for product {}: {} → {}",
                         productId, oldPrice, newPrice);
        } catch (SQLException e) {
            logger.error("record price history: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns all price history for a product, newest first (max 100 rows).
     */
    public List<PriceHistoryEntry> findByProduct(int productId) {
        List<PriceHistoryEntry> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_PRODUCT)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PriceHistoryEntry e = new PriceHistoryEntry();
                    e.setId(rs.getInt("id"));
                    e.setProductId(rs.getInt("product_id"));
                    e.setOldPrice(rs.getBigDecimal("old_price"));
                    e.setNewPrice(rs.getBigDecimal("new_price"));
                    e.setOldCost(rs.getBigDecimal("old_cost"));
                    e.setNewCost(rs.getBigDecimal("new_cost"));
                    e.setChangedBy(rs.getInt("changed_by"));
                    e.setChangedByName(rs.getString("changed_by_name"));
                    e.setReason(rs.getString("reason"));
                    Timestamp ts = rs.getTimestamp("changed_at");
                    if (ts != null) e.setChangedAt(ts.toLocalDateTime());
                    list.add(e);
                }
            }
        } catch (SQLException e) {
            logger.error("findByProduct price history: {}", e.getMessage(), e);
        }
        return list;
    }
}
