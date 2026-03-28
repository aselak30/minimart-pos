package com.minimartpos.service;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Bill;
import com.minimartpos.model.DashboardStats;
import com.minimartpos.model.Product;
import com.minimartpos.repository.BillRepository;
import com.minimartpos.repository.ProductRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides all aggregated data queries for the Admin Dashboard.
 *
 * Each method targets a specific dashboard widget:
 *   - loadStats()          → all KPI numbers for today + yesterday
 *   - getSalesByHour()     → hourly breakdown for a given date
 *   - getLowStockProducts()→ products at or below reorder level
 *   - getExpiringProducts()→ products expiring within N days
 *   - getRecentBills()     → last N finalized bills across all machines
 *   - getActiveSessions()  → open shifts (proxy for active cashiers)
 */
public class DashboardService {

    private static final Logger logger = LogManager.getLogger(DashboardService.class);

    // ── KPI Aggregates ────────────────────────────────────────────────────────

    private static final String SQL_DAY_STATS =
        "SELECT " +
        "  COALESCE(SUM(total_amount), 0)  AS total_sales, " +
        "  COUNT(*)                         AS bill_count, " +
        "  COALESCE(AVG(total_amount), 0)  AS avg_bill, " +
        "  COALESCE(SUM(profit_total), 0)  AS total_profit " +
        "FROM bills " +
        "WHERE status = 'FINALIZED' AND DATE(finalized_at) = ?";

    /**
     * Loads all KPI numbers for today and yesterday in one shot.
     */
    public DashboardStats loadStats() {
        DashboardStats stats = new DashboardStats();
        String today     = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        try (Connection conn = DatabaseConfig.getConnection()) {

            // Today
            try (PreparedStatement ps = conn.prepareStatement(SQL_DAY_STATS)) {
                ps.setString(1, today);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BigDecimal sales  = rs.getBigDecimal("total_sales");
                        BigDecimal profit = rs.getBigDecimal("total_profit");
                        int        bills  = rs.getInt("bill_count");
                        BigDecimal avg    = rs.getBigDecimal("avg_bill");

                        stats.setTodaySales(sales);
                        stats.setTodayBillCount(bills);
                        stats.setTodayAvgBill(avg.setScale(2, RoundingMode.HALF_UP));
                        stats.setTodayProfit(profit);

                        // Profit margin %
                        if (sales.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal pct = profit.divide(sales, 4, RoundingMode.HALF_UP)
                                                   .multiply(BigDecimal.valueOf(100))
                                                   .setScale(1, RoundingMode.HALF_UP);
                            stats.setTodayProfitPct(pct);
                        }
                    }
                }
            }

            // Yesterday (for delta badges)
            try (PreparedStatement ps = conn.prepareStatement(SQL_DAY_STATS)) {
                ps.setString(1, yesterday);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.setYesterdaySales(rs.getBigDecimal("total_sales"));
                        stats.setYesterdayBillCount(rs.getInt("bill_count"));
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("loadStats error: {}", e.getMessage(), e);
        }

        // Counts
        stats.setLowStockCount(getLowStockProducts().size());
        stats.setExpiringCount(getExpiringProducts(30).size());
        stats.setActiveCashierCount(getActiveShiftCount());
        stats.setSalesByHour(getSalesByHour(LocalDate.now()));

        return stats;
    }

    // ── Sales by Hour Chart ───────────────────────────────────────────────────

    private static final String SQL_SALES_BY_HOUR =
        "SELECT HOUR(finalized_at) AS hr, SUM(total_amount) AS total " +
        "FROM bills " +
        "WHERE status='FINALIZED' AND DATE(finalized_at)=? " +
        "GROUP BY HOUR(finalized_at) ORDER BY hr";

    public Map<String, BigDecimal> getSalesByHour(LocalDate date) {
        // Start with all 24 hours at zero
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) result.put(String.format("%02d:00", h), BigDecimal.ZERO);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SALES_BY_HOUR)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = String.format("%02d:00", rs.getInt("hr"));
                    result.put(key, rs.getBigDecimal("total").setScale(2, RoundingMode.HALF_UP));
                }
            }
        } catch (SQLException e) {
            logger.error("getSalesByHour error: {}", e.getMessage(), e);
        }
        return result;
    }

    // ── Low Stock ─────────────────────────────────────────────────────────────

    private static final String SQL_LOW_STOCK =
        "SELECT p.id, p.name, p.barcode, p.stock_quantity, p.reorder_level, " +
        "       c.name AS category_name " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id " +
        "WHERE p.active=1 AND p.stock_quantity <= p.reorder_level " +
        "ORDER BY p.stock_quantity ASC " +
        "LIMIT 50";

    public List<Product> getLowStockProducts() {
        List<Product> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LOW_STOCK);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Product p = new Product();
                p.setId(rs.getInt("id"));
                p.setName(rs.getString("name"));
                p.setBarcode(rs.getString("barcode"));
                p.setStockQuantity(rs.getBigDecimal("stock_quantity"));
                p.setReorderLevel(rs.getBigDecimal("reorder_level"));
                p.setCategoryName(rs.getString("category_name"));
                list.add(p);
            }
        } catch (SQLException e) {
            logger.error("getLowStockProducts error: {}", e.getMessage(), e);
        }
        return list;
    }

    // ── Expiring Products ─────────────────────────────────────────────────────

    private static final String SQL_EXPIRING =
        "SELECT p.id, p.name, p.batch_number, p.stock_quantity, p.expiry_date, " +
        "       c.name AS category_name, " +
        "       DATEDIFF(p.expiry_date, CURDATE()) AS days_left " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id " +
        "WHERE p.active=1 AND p.expiry_date IS NOT NULL " +
        "  AND p.expiry_date >= CURDATE() " +
        "  AND DATEDIFF(p.expiry_date, CURDATE()) <= ? " +
        "ORDER BY p.expiry_date ASC " +
        "LIMIT 50";

    public List<Map<String, Object>> getExpiringProducts(int withinDays) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_EXPIRING)) {
            ps.setInt(1, withinDays);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id",           rs.getInt("id"));
                    row.put("name",         rs.getString("name"));
                    row.put("batch",        rs.getString("batch_number"));
                    row.put("stock",        rs.getBigDecimal("stock_quantity"));
                    row.put("expiryDate",   rs.getDate("expiry_date") != null
                                            ? rs.getDate("expiry_date").toLocalDate() : null);
                    row.put("daysLeft",     rs.getInt("days_left"));
                    row.put("category",     rs.getString("category_name"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("getExpiringProducts error: {}", e.getMessage(), e);
        }
        return list;
    }

    // ── Recent Transactions ───────────────────────────────────────────────────

    private static final String SQL_RECENT_BILLS =
        "SELECT b.id, b.bill_number, b.total_amount, b.payment_type, b.status, " +
        "       b.finalized_at, u.full_name AS cashier_name " +
        "FROM bills b " +
        "LEFT JOIN users u ON b.cashier_id = u.id " +
        "WHERE b.status IN ('FINALIZED','VOIDED') " +
        "ORDER BY b.finalized_at DESC " +
        "LIMIT ?";

    public List<Map<String, Object>> getRecentBills(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RECENT_BILLS)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("billNumber",   rs.getString("bill_number"));
                    row.put("cashier",      rs.getString("cashier_name"));
                    row.put("total",        rs.getBigDecimal("total_amount"));
                    row.put("paymentType",  rs.getString("payment_type"));
                    row.put("status",       rs.getString("status"));
                    Timestamp ts = rs.getTimestamp("finalized_at");
                    row.put("time", ts != null
                            ? ts.toLocalDateTime().format(timeFmt) : "—");
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("getRecentBills error: {}", e.getMessage(), e);
        }
        return list;
    }

    // ── Active Sessions ───────────────────────────────────────────────────────

    private static final String SQL_ACTIVE_SHIFTS =
        "SELECT s.id, s.start_time, s.opening_cash, " +
        "       u.full_name AS cashier_name, " +
        "       m.machine_name, m.ip_address, " +
        "       COALESCE(" +
        "         (SELECT SUM(b.total_amount) FROM bills b " +
        "          WHERE b.cashier_id=s.cashier_id AND DATE(b.finalized_at)=CURDATE()" +
        "          AND b.status='FINALIZED'), 0" +
        "       ) AS shift_sales, " +
        "       COALESCE(" +
        "         (SELECT COUNT(*) FROM bills b " +
        "          WHERE b.cashier_id=s.cashier_id AND DATE(b.finalized_at)=CURDATE()" +
        "          AND b.status='FINALIZED'), 0" +
        "       ) AS shift_bills " +
        "FROM shifts s " +
        "LEFT JOIN users   u ON s.cashier_id  = u.id " +
        "LEFT JOIN machines m ON s.machine_id = m.id " +
        "WHERE s.status='OPEN' " +
        "ORDER BY s.start_time";

    public List<Map<String, Object>> getActiveSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ACTIVE_SHIFTS);
             ResultSet rs = ps.executeQuery()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("cashierName",  rs.getString("cashier_name"));
                row.put("machineName",  rs.getString("machine_name"));
                row.put("ipAddress",    rs.getString("ip_address"));
                row.put("shiftSales",   rs.getBigDecimal("shift_sales"));
                row.put("shiftBills",   rs.getInt("shift_bills"));
                Timestamp ts = rs.getTimestamp("start_time");
                row.put("startTime", ts != null
                        ? ts.toLocalDateTime().format(fmt) : "—");
                list.add(row);
            }
        } catch (SQLException e) {
            logger.error("getActiveSessions error: {}", e.getMessage(), e);
        }
        return list;
    }

    private int getActiveShiftCount() {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM shifts WHERE status='OPEN'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            logger.error("getActiveShiftCount error: {}", e.getMessage(), e);
            return 0;
        }
    }
}
