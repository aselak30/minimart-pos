package com.minimartpos.service;

import com.minimartpos.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Provides live cashier cash collection data for Admin monitoring.
 *
 * Tracks per-cashier:
 *  - Total cash collected today (cash sales only)
 *  - Total all-payment sales today
 *  - Number of bills processed
 *  - Cash limit set for the cashier
 *  - Whether the cashier has exceeded their limit
 *  - Last sale time
 *  - Current shift status
 *
 * Used by AdminDashboardController to refresh the live monitor panel.
 */
public class CashierMonitorService {

    private static final Logger logger = LogManager.getLogger(CashierMonitorService.class);

    /**
     * Snapshot of a single cashier's current day activity.
     */
    public static class CashierSnapshot {
        private int           cashierId;
        private String        cashierName;
        private BigDecimal    cashCollected  = BigDecimal.ZERO; // cash payment type only
        private BigDecimal    totalSales     = BigDecimal.ZERO; // all payment types
        private BigDecimal    cashLimit      = BigDecimal.ZERO; // 0 = no limit
        private BigDecimal    dailyTarget    = BigDecimal.ZERO;
        private int           billCount      = 0;
        private LocalDateTime lastSaleTime;
        private String        shiftStatus    = "NO_SHIFT"; // OPEN, CLOSED, NO_SHIFT
        private BigDecimal    openingCash    = BigDecimal.ZERO;
        private boolean       limitExceeded  = false;
        private boolean       nearLimit      = false; // within 10% of limit

        // ── Getters ───────────────────────────────────────────────────────────
        public int            getCashierId()     { return cashierId; }
        public String         getCashierName()   { return cashierName; }
        public BigDecimal     getCashCollected() { return cashCollected; }
        public BigDecimal     getTotalSales()    { return totalSales; }
        public BigDecimal     getCashLimit()     { return cashLimit; }
        public BigDecimal     getDailyTarget()   { return dailyTarget; }
        public int            getBillCount()     { return billCount; }
        public LocalDateTime  getLastSaleTime()  { return lastSaleTime; }
        public String         getShiftStatus()   { return shiftStatus; }
        public BigDecimal     getOpeningCash()   { return openingCash; }
        public boolean        isLimitExceeded()  { return limitExceeded; }
        public boolean        isNearLimit()      { return nearLimit; }

        /** Percentage of cash limit used (0-100+). Returns 0 if no limit set. */
        public double getLimitUsedPercent() {
            if (cashLimit.compareTo(BigDecimal.ZERO) == 0) return 0;
            return cashCollected.divide(cashLimit, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .doubleValue();
        }

        /** Remaining cash before hitting limit. Returns null if no limit. */
        public BigDecimal getCashRemaining() {
            if (cashLimit.compareTo(BigDecimal.ZERO) == 0) return null;
            BigDecimal remaining = cashLimit.subtract(cashCollected);
            return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
        }

        /** Target achievement percentage. Returns 0 if no target set. */
        public double getTargetPercent() {
            if (dailyTarget.compareTo(BigDecimal.ZERO) == 0) return 0;
            return totalSales.divide(dailyTarget, 4, java.math.RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(100))
                             .doubleValue();
        }

        // ── Package-private setters (used by service) ─────────────────────────
        void setCashierId(int v)                 { this.cashierId = v; }
        void setCashierName(String v)            { this.cashierName = v; }
        void setCashCollected(BigDecimal v)      { this.cashCollected = v != null ? v : BigDecimal.ZERO; }
        void setTotalSales(BigDecimal v)         { this.totalSales = v != null ? v : BigDecimal.ZERO; }
        void setCashLimit(BigDecimal v)          { this.cashLimit = v != null ? v : BigDecimal.ZERO; }
        void setDailyTarget(BigDecimal v)        { this.dailyTarget = v != null ? v : BigDecimal.ZERO; }
        void setBillCount(int v)                 { this.billCount = v; }
        void setLastSaleTime(LocalDateTime v)    { this.lastSaleTime = v; }
        void setShiftStatus(String v)            { this.shiftStatus = v; }
        void setOpeningCash(BigDecimal v)        { this.openingCash = v != null ? v : BigDecimal.ZERO; }
        void computeAlerts() {
            if (cashLimit.compareTo(BigDecimal.ZERO) > 0) {
                limitExceeded = cashCollected.compareTo(cashLimit) >= 0;
                // Near limit = within 10% remaining
                BigDecimal ninetyPct = cashLimit.multiply(BigDecimal.valueOf(0.9));
                nearLimit = !limitExceeded && cashCollected.compareTo(ninetyPct) >= 0;
            }
        }
    }

    // ── Main query ────────────────────────────────────────────────────────────

    /**
     * Returns live snapshots for all active cashiers (those with a shift open today
     * or who have processed bills today).
     */
    public List<CashierSnapshot> getLiveCashierData() {
        List<CashierSnapshot> list = new ArrayList<>();

        String sql =
            "SELECT " +
            "  u.id AS cashier_id, " +
            "  u.full_name AS cashier_name, " +
            "  u.cash_limit, " +
            "  u.daily_sales_target, " +
            "  COALESCE(SUM(CASE WHEN b.payment_type='CASH' AND b.status='FINALIZED' " +
            "                   THEN b.total_amount ELSE 0 END), 0) AS cash_collected, " +
            "  COALESCE(SUM(CASE WHEN b.status='FINALIZED' " +
            "                   THEN b.total_amount ELSE 0 END), 0) AS total_sales, " +
            "  COALESCE(SUM(CASE WHEN b.status='FINALIZED' THEN 1 ELSE 0 END), 0) AS bill_count, " +
            "  MAX(b.finalized_at) AS last_sale_time, " +
            "  s.status AS shift_status, " +
            "  s.opening_cash " +
            "FROM users u " +
            "LEFT JOIN bills b ON b.cashier_id = u.id " +
            "     AND DATE(b.created_at) = CURDATE() " +
            "LEFT JOIN shifts s ON s.cashier_id = u.id " +
            "     AND DATE(s.start_time) = CURDATE() " +
            "     AND s.status = 'OPEN' " +
            "WHERE u.role = 'CASHIER' AND u.active = 1 " +
            "GROUP BY u.id, u.full_name, u.cash_limit, u.daily_sales_target, " +
            "         s.status, s.opening_cash " +
            "ORDER BY cash_collected DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                CashierSnapshot snap = new CashierSnapshot();
                snap.setCashierId(rs.getInt("cashier_id"));
                snap.setCashierName(rs.getString("cashier_name"));
                snap.setCashCollected(rs.getBigDecimal("cash_collected"));
                snap.setTotalSales(rs.getBigDecimal("total_sales"));
                snap.setCashLimit(rs.getBigDecimal("cash_limit"));
                snap.setDailyTarget(rs.getBigDecimal("daily_sales_target"));
                snap.setBillCount(rs.getInt("bill_count"));
                snap.setShiftStatus(rs.getString("shift_status") != null
                    ? rs.getString("shift_status") : "NO_SHIFT");
                snap.setOpeningCash(rs.getBigDecimal("opening_cash"));
                Timestamp lastSale = rs.getTimestamp("last_sale_time");
                if (lastSale != null) snap.setLastSaleTime(lastSale.toLocalDateTime());
                snap.computeAlerts();
                list.add(snap);
            }

        } catch (SQLException e) {
            logger.error("getLiveCashierData error: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * Returns the snapshot for a single cashier.
     */
    public Optional<CashierSnapshot> getSnapshotForCashier(int cashierId) {
        return getLiveCashierData().stream()
            .filter(s -> s.getCashierId() == cashierId)
            .findFirst();
    }

    /**
     * Returns all cashiers who have exceeded their cash limit.
     */
    public List<CashierSnapshot> getExceededLimitCashiers() {
        List<CashierSnapshot> exceeded = new ArrayList<>();
        for (CashierSnapshot s : getLiveCashierData()) {
            if (s.isLimitExceeded()) exceeded.add(s);
        }
        return exceeded;
    }

    /**
     * Returns cashiers who are near their limit (within 10%).
     */
    public List<CashierSnapshot> getNearLimitCashiers() {
        List<CashierSnapshot> near = new ArrayList<>();
        for (CashierSnapshot s : getLiveCashierData()) {
            if (s.isNearLimit()) near.add(s);
        }
        return near;
    }
}
