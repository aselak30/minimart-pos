package com.minimartpos.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated statistics for the Admin Dashboard.
 * Populated by DashboardService and bound to the FXML controller.
 */
public class DashboardStats {

    // ── Today KPIs ────────────────────────────────────────────────────────────
    private BigDecimal todaySales       = BigDecimal.ZERO;
    private int        todayBillCount   = 0;
    private BigDecimal todayAvgBill     = BigDecimal.ZERO;
    private BigDecimal todayProfit      = BigDecimal.ZERO;
    private BigDecimal todayProfitPct   = BigDecimal.ZERO;   // margin %

    // ── Yesterday KPIs (for delta) ────────────────────────────────────────────
    private BigDecimal yesterdaySales      = BigDecimal.ZERO;
    private int        yesterdayBillCount  = 0;

    // ── Chart data: hour (0–23) → total sales ─────────────────────────────────
    private Map<String, BigDecimal> salesByHour = new LinkedHashMap<>();

    // ── Cashier / machine status ──────────────────────────────────────────────
    private int activeCashierCount = 0;

    // ── Counts (computed by service) ─────────────────────────────────────────
    private int lowStockCount   = 0;
    private int expiringCount   = 0;

    // ── Constructors ──────────────────────────────────────────────────────────
    public DashboardStats() {
        // Pre-populate all 24 hours with zero so the chart always shows full axis
        for (int h = 0; h < 24; h++) {
            salesByHour.put(String.format("%02d:00", h), BigDecimal.ZERO);
        }
    }

    // ── Derived helpers ───────────────────────────────────────────────────────

    public BigDecimal getSalesDelta() {
        return todaySales.subtract(yesterdaySales);
    }

    public int getBillsDelta() {
        return todayBillCount - yesterdayBillCount;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public BigDecimal getTodaySales()                              { return todaySales; }
    public void       setTodaySales(BigDecimal v)                  { this.todaySales = v; }

    public int  getTodayBillCount()                                { return todayBillCount; }
    public void setTodayBillCount(int v)                           { this.todayBillCount = v; }

    public BigDecimal getTodayAvgBill()                            { return todayAvgBill; }
    public void       setTodayAvgBill(BigDecimal v)                { this.todayAvgBill = v; }

    public BigDecimal getTodayProfit()                             { return todayProfit; }
    public void       setTodayProfit(BigDecimal v)                 { this.todayProfit = v; }

    public BigDecimal getTodayProfitPct()                          { return todayProfitPct; }
    public void       setTodayProfitPct(BigDecimal v)              { this.todayProfitPct = v; }

    public BigDecimal getYesterdaySales()                          { return yesterdaySales; }
    public void       setYesterdaySales(BigDecimal v)              { this.yesterdaySales = v; }

    public int  getYesterdayBillCount()                            { return yesterdayBillCount; }
    public void setYesterdayBillCount(int v)                       { this.yesterdayBillCount = v; }

    public Map<String, BigDecimal> getSalesByHour()                { return salesByHour; }
    public void setSalesByHour(Map<String, BigDecimal> v)          { this.salesByHour = v; }
    public void putHourSales(String hour, BigDecimal amount)       { salesByHour.put(hour, amount); }

    public int  getActiveCashierCount()                            { return activeCashierCount; }
    public void setActiveCashierCount(int v)                       { this.activeCashierCount = v; }

    public int  getLowStockCount()                                 { return lowStockCount; }
    public void setLowStockCount(int v)                            { this.lowStockCount = v; }

    public int  getExpiringCount()                                 { return expiringCount; }
    public void setExpiringCount(int v)                            { this.expiringCount = v; }
}
