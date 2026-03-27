package com.minimartpos.service;

import com.minimartpos.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Provides all data queries needed by the Reports screen.
 *
 * Reports covered:
 *  - Sales Summary (by date range): total, profit, bills, avg
 *  - Sales by Cashier
 *  - Sales by Category
 *  - Sales by Payment Type
 *  - Daily Sales trend (line chart data)
 *  - Top Selling Products
 *  - Stock Movement Summary
 *  - Shift Summary
 */
public class ReportService {

    private static final Logger logger = LogManager.getLogger(ReportService.class);

    // ── Sales Summary ─────────────────────────────────────────────────────────

    public Map<String, Object> getSalesSummary(LocalDate from, LocalDate to) {
        Map<String, Object> result = new LinkedHashMap<>();
        String sql =
            "SELECT COUNT(*) AS bill_count, " +
            "  COALESCE(SUM(total_amount),0) AS total_sales, " +
            "  COALESCE(SUM(profit_total),0) AS total_profit, " +
            "  COALESCE(AVG(total_amount),0) AS avg_bill, " +
            "  COALESCE(SUM(discount_amount),0) AS total_discount, " +
            "  COALESCE(SUM(tax_amount),0) AS total_tax " +
            "FROM bills WHERE status='FINALIZED' " +
            "AND DATE(finalized_at) BETWEEN ? AND ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("billCount",     rs.getInt("bill_count"));
                    result.put("totalSales",    rs.getBigDecimal("total_sales"));
                    result.put("totalProfit",   rs.getBigDecimal("total_profit"));
                    result.put("avgBill",       rs.getBigDecimal("avg_bill"));
                    result.put("totalDiscount", rs.getBigDecimal("total_discount"));
                    result.put("totalTax",      rs.getBigDecimal("total_tax"));
                }
            }
        } catch (SQLException e) { logger.error("getSalesSummary: {}", e.getMessage(), e); }
        return result;
    }

    // ── Daily Trend ───────────────────────────────────────────────────────────

    public List<Map<String, Object>> getDailyTrend(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql =
            "SELECT DATE(finalized_at) AS day, " +
            "  COUNT(*) AS bill_count, " +
            "  COALESCE(SUM(total_amount),0) AS total_sales, " +
            "  COALESCE(SUM(profit_total),0) AS total_profit " +
            "FROM bills WHERE status='FINALIZED' " +
            "AND DATE(finalized_at) BETWEEN ? AND ? " +
            "GROUP BY DATE(finalized_at) ORDER BY day";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("day",        rs.getDate("day").toLocalDate());
                    row.put("billCount",  rs.getInt("bill_count"));
                    row.put("sales",      rs.getBigDecimal("total_sales"));
                    row.put("profit",     rs.getBigDecimal("total_profit"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) { logger.error("getDailyTrend: {}", e.getMessage(), e); }
        return rows;
    }

    // ── Sales by Cashier ──────────────────────────────────────────────────────

    public List<Map<String, Object>> getSalesByCashier(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql =
            "SELECT u.full_name, COUNT(*) AS bills, " +
            "  COALESCE(SUM(b.total_amount),0) AS sales, " +
            "  COALESCE(SUM(b.profit_total),0) AS profit " +
            "FROM bills b LEFT JOIN users u ON b.cashier_id=u.id " +
            "WHERE b.status='FINALIZED' AND DATE(b.finalized_at) BETWEEN ? AND ? " +
            "GROUP BY b.cashier_id, u.full_name ORDER BY sales DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cashier", rs.getString("full_name"));
                    row.put("bills",   rs.getInt("bills"));
                    row.put("sales",   rs.getBigDecimal("sales"));
                    row.put("profit",  rs.getBigDecimal("profit"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) { logger.error("getSalesByCashier: {}", e.getMessage(), e); }
        return rows;
    }

    // ── Sales by Category ─────────────────────────────────────────────────────

    public List<Map<String, Object>> getSalesByCategory(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql =
            "SELECT c.name AS category, " +
            "  SUM(bi.quantity) AS qty_sold, " +
            "  COALESCE(SUM(bi.line_total),0) AS sales " +
            "FROM bill_items bi " +
            "JOIN bills b ON bi.bill_id=b.id " +
            "JOIN products p ON bi.product_id=p.id " +
            "LEFT JOIN categories c ON p.category_id=c.id " +
            "WHERE b.status='FINALIZED' AND DATE(b.finalized_at) BETWEEN ? AND ? " +
            "GROUP BY p.category_id, c.name ORDER BY sales DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", rs.getString("category"));
                    row.put("qtySold",  rs.getInt("qty_sold"));
                    row.put("sales",    rs.getBigDecimal("sales"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) { logger.error("getSalesByCategory: {}", e.getMessage(), e); }
        return rows;
    }

    // ── Sales by Payment Type ─────────────────────────────────────────────────

    public List<Map<String, Object>> getSalesByPaymentType(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql =
            "SELECT payment_type, COUNT(*) AS bills, " +
            "  COALESCE(SUM(total_amount),0) AS total " +
            "FROM bills WHERE status='FINALIZED' AND DATE(finalized_at) BETWEEN ? AND ? " +
            "GROUP BY payment_type ORDER BY total DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type",  rs.getString("payment_type"));
                    row.put("bills", rs.getInt("bills"));
                    row.put("total", rs.getBigDecimal("total"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) { logger.error("getSalesByPayment: {}", e.getMessage(), e); }
        return rows;
    }

    // ── Top Products ──────────────────────────────────────────────────────────

    public List<Map<String, Object>> getTopProducts(LocalDate from, LocalDate to, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql =
            "SELECT bi.product_name, " +
            "  SUM(bi.quantity) AS qty_sold, " +
            "  COALESCE(SUM(bi.line_total),0) AS revenue, " +
            "  COALESCE(SUM(bi.line_total - bi.cost_price*bi.quantity),0) AS profit " +
            "FROM bill_items bi " +
            "JOIN bills b ON bi.bill_id=b.id " +
            "WHERE b.status='FINALIZED' AND DATE(b.finalized_at) BETWEEN ? AND ? " +
            "GROUP BY bi.product_id, bi.product_name " +
            "ORDER BY qty_sold DESC LIMIT ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("product", rs.getString("product_name"));
                    row.put("qtySold", rs.getInt("qty_sold"));
                    row.put("revenue", rs.getBigDecimal("revenue"));
                    row.put("profit",  rs.getBigDecimal("profit"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) { logger.error("getTopProducts: {}", e.getMessage(), e); }
        return rows;
    }

    // ── Shift Summary ─────────────────────────────────────────────────────────

    public List<Map<String, Object>> getShiftSummary(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql =
            "SELECT s.id, u.full_name AS cashier, s.start_time, s.end_time, " +
            "  s.opening_cash, s.closing_cash, s.status, " +
            "  COALESCE((" +
            "    SELECT SUM(b.total_amount) FROM bills b " +
            "    WHERE b.cashier_id=s.cashier_id AND b.status='FINALIZED' " +
            "    AND DATE(b.finalized_at)=DATE(s.start_time)" +
            "  ),0) AS shift_sales, " +
            "  COALESCE((" +
            "    SELECT COUNT(*) FROM bills b " +
            "    WHERE b.cashier_id=s.cashier_id AND b.status='FINALIZED' " +
            "    AND DATE(b.finalized_at)=DATE(s.start_time)" +
            "  ),0) AS shift_bills " +
            "FROM shifts s LEFT JOIN users u ON s.cashier_id=u.id " +
            "WHERE DATE(s.start_time) BETWEEN ? AND ? ORDER BY s.start_time DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cashier",     rs.getString("cashier"));
                    row.put("startTime",   rs.getTimestamp("start_time") != null
                                           ? rs.getTimestamp("start_time").toLocalDateTime() : null);
                    row.put("endTime",     rs.getTimestamp("end_time") != null
                                           ? rs.getTimestamp("end_time").toLocalDateTime() : null);
                    row.put("openingCash", rs.getBigDecimal("opening_cash"));
                    row.put("closingCash", rs.getBigDecimal("closing_cash"));
                    row.put("shiftSales",  rs.getBigDecimal("shift_sales"));
                    row.put("shiftBills",  rs.getInt("shift_bills"));
                    row.put("status",      rs.getString("status"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) { logger.error("getShiftSummary: {}", e.getMessage(), e); }
        return rows;
    }
}
