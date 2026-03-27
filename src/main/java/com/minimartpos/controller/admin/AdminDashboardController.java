package com.minimartpos.controller.admin;

import com.minimartpos.model.DashboardStats;
import com.minimartpos.model.Product;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.DashboardService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import com.minimartpos.util.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the Admin Dashboard screen.
 *
 * Responsibilities:
 *  - Live clock in top bar
 *  - KPI cards: today's sales, bill count, avg bill, profit + margin
 *  - Delta badges vs yesterday (↑ green / ↓ red)
 *  - Hourly sales BarChart (refreshable by date picker)
 *  - Live cashier / machine status panel
 *  - Low stock alerts table
 *  - Recent transactions table
 *  - Expiry alerts table (configurable days)
 *  - Sidebar navigation to all admin screens
 *  - Auto-refresh every 60 seconds
 */
public class AdminDashboardController implements Initializable {

    private static final Logger logger = LogManager.getLogger(AdminDashboardController.class);

    // ── FXML: Top Bar ─────────────────────────────────────────────────────────
    @FXML private Label    pageTitleLabel;
    @FXML private Label    clockLabel;
    @FXML private Label    dateLabel;

    // ── FXML: Sidebar ─────────────────────────────────────────────────────────
    @FXML private Label    sidebarUserLabel;
    @FXML private Label    sidebarVersionLabel;
    @FXML private Button   navDashboard;
    @FXML private Button   navUsers;
    @FXML private Button   navProducts;
    @FXML private Button   navCustomers;
    @FXML private Button   navBills;
    @FXML private Button   navStock;
    @FXML private Button   navReports;
    @FXML private Button   navAudit;
    @FXML private Button   navSettings;
    @FXML private Button   navSuppliers;
    @FXML private Button   navMonitor;
    @FXML private Button   navPOS;

    // ── FXML: KPI Cards ───────────────────────────────────────────────────────
    @FXML private Label kpiSalesLabel;
    @FXML private Label kpiSalesDeltaLabel;
    @FXML private Label kpiBillsLabel;
    @FXML private Label kpiBillsDeltaLabel;
    @FXML private Label kpiAvgLabel;
    @FXML private Label kpiProfitLabel;
    @FXML private Label kpiMarginLabel;

    // ── FXML: Chart ───────────────────────────────────────────────────────────
    @FXML private BarChart<String, Number> salesChart;
    @FXML private ComboBox<String>         chartDatePicker;

    // ── FXML: Machine Status ──────────────────────────────────────────────────
    @FXML private VBox  machineStatusBox;
    @FXML private Label activeCashiersLabel;

    // ── FXML: Low Stock Table ─────────────────────────────────────────────────
    @FXML private TableView<Product>            lowStockTable;
    @FXML private TableColumn<Product, String>  lsColProduct;
    @FXML private TableColumn<Product, String>  lsColCategory;
    @FXML private TableColumn<Product, String>  lsColStock;
    @FXML private TableColumn<Product, String>  lsColReorder;
    @FXML private Label                         lowStockCountLabel;

    // ── FXML: Recent Bills Table ──────────────────────────────────────────────
    @FXML private TableView<Map<String, Object>>           recentBillsTable;
    @FXML private TableColumn<Map<String, Object>, String> rbColBill;
    @FXML private TableColumn<Map<String, Object>, String> rbColCashier;
    @FXML private TableColumn<Map<String, Object>, String> rbColTotal;
    @FXML private TableColumn<Map<String, Object>, String> rbColPayment;
    @FXML private TableColumn<Map<String, Object>, String> rbColTime;
    @FXML private TableColumn<Map<String, Object>, String> rbColStatus;

    // ── FXML: Expiry Table ────────────────────────────────────────────────────
    @FXML private TableView<Map<String, Object>>           expiryTable;
    @FXML private TableColumn<Map<String, Object>, String> exColProduct;
    @FXML private TableColumn<Map<String, Object>, String> exColBatch;
    @FXML private TableColumn<Map<String, Object>, String> exColStock;
    @FXML private TableColumn<Map<String, Object>, String> exColExpiry;
    @FXML private TableColumn<Map<String, Object>, String> exColDaysLeft;
    @FXML private Label                                    expiryCountLabel;
    @FXML private ComboBox<String>                         expiryDaysCombo;

    // ── State ─────────────────────────────────────────────────────────────────
    private final DashboardService dashboardService = new DashboardService();
    private Timeline clockTimeline;
    private Timeline autoRefreshTimeline;
    private int expiryDays = 30;

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupSidebar();
        setupClock();
        setupChartDatePicker();
        setupExpiryDaysCombo();
        setupTableColumns();
        setActiveNav(navDashboard);
        refreshDashboard();
        setupAutoRefresh();
        logger.info("Admin Dashboard initialized");
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupSidebar() {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        sidebarVersionLabel.setText("v1.0.0");
    }

    private void setupClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            LocalDateTime now = LocalDateTime.now();
            clockLabel.setText(now.format(DateUtil.CLOCK_TIME));
            dateLabel.setText(now.format(DateUtil.TOPBAR_DATE));
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void setupAutoRefresh() {
        autoRefreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(60), e -> refreshDashboard()));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();

        // Live sync: refresh dashboard when bills finalized or stock changes on other machines
        com.minimartpos.network.SyncManager sm =
            com.minimartpos.network.SyncManager.getInstance();
        sm.addListener(com.minimartpos.network.SyncEvent.Type.BILL_FINALIZED,
            event -> javafx.application.Platform.runLater(this::refreshDashboard));
        sm.addListener(com.minimartpos.network.SyncEvent.Type.SETTINGS_CHANGED,
            event -> javafx.application.Platform.runLater(this::refreshDashboard));

        // Machine status changes → update the cashier panel immediately
        sm.getNetworkMonitor().addStatusListener(machine ->
            javafx.application.Platform.runLater(() -> {
                try {
                    updateMachineStatus(
                        sm.getNetworkMonitor().getOnlineMachines().stream()
                          .map(m -> {
                              java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                              row.put("cashierName", m.getCurrentUser() != null
                                  ? m.getCurrentUser() : m.getMachineCode());
                              row.put("machineName", m.getMachineName() != null
                                  ? m.getMachineName() : m.getMachineCode());
                              row.put("ipAddress",   m.getIpAddress());
                              row.put("shiftSales",  java.math.BigDecimal.ZERO);
                              row.put("shiftBills",  0);
                              row.put("startTime",   "—");
                              return row;
                          }).toList());
                    activeCashiersLabel.setText(
                        sm.getNetworkMonitor().getOnlineCount() + " active");
                } catch (Exception ignored) {}
            })
        );
    }

    private void setupChartDatePicker() {
        chartDatePicker.setItems(FXCollections.observableArrayList(
            "Today", "Yesterday", "Last 7 days avg"
        ));
        chartDatePicker.getSelectionModel().selectFirst();
    }

    private void setupExpiryDaysCombo() {
        expiryDaysCombo.setItems(FXCollections.observableArrayList(
            "Expiring in 7 days", "Expiring in 14 days",
            "Expiring in 30 days", "Expiring in 60 days"
        ));
        expiryDaysCombo.getSelectionModel().select(2); // default 30 days
    }

    private void setupTableColumns() {
        // ── Low Stock table ──────────────────────────────────────────────────
        lsColProduct.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getName()));
        lsColCategory.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCategoryName()));
        lsColStock.setCellValueFactory(c ->
            new SimpleStringProperty(String.valueOf(c.getValue().getStockQuantity())));
        lsColStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                // Red if zero, orange if low
                int qty = Integer.parseInt(v);
                setStyle(qty == 0
                    ? "-fx-text-fill:-pos-danger; -fx-font-weight:bold;"
                    : "-fx-text-fill:-pos-warning; -fx-font-weight:bold;");
            }
        });
        lsColReorder.setCellValueFactory(c ->
            new SimpleStringProperty(String.valueOf(c.getValue().getReorderLevel())));

        // ── Recent Bills table ───────────────────────────────────────────────
        rbColBill.setCellValueFactory(c ->
            new SimpleStringProperty((String) c.getValue().get("billNumber")));
        rbColCashier.setCellValueFactory(c ->
            new SimpleStringProperty((String) c.getValue().get("cashier")));
        rbColTotal.setCellValueFactory(c -> {
            Object v = c.getValue().get("total");
            return new SimpleStringProperty(v != null
                ? CurrencyUtil.format((BigDecimal) v) : "—");
        });
        rbColPayment.setCellValueFactory(c ->
            new SimpleStringProperty(formatPaymentType((String) c.getValue().get("paymentType"))));
        rbColTime.setCellValueFactory(c ->
            new SimpleStringProperty((String) c.getValue().get("time")));
        rbColStatus.setCellValueFactory(c ->
            new SimpleStringProperty((String) c.getValue().get("status")));
        rbColStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle(v.equals("VOIDED")
                    ? "-fx-text-fill:-pos-danger;"
                    : "-fx-text-fill:-pos-success;");
            }
        });

        // ── Expiry table ─────────────────────────────────────────────────────
        exColProduct.setCellValueFactory(c ->
            new SimpleStringProperty((String) c.getValue().get("name")));
        exColBatch.setCellValueFactory(c -> {
            String b = (String) c.getValue().get("batch");
            return new SimpleStringProperty(b != null ? b : "—");
        });
        exColStock.setCellValueFactory(c ->
            new SimpleStringProperty(String.valueOf(c.getValue().get("stock"))));
        exColExpiry.setCellValueFactory(c -> {
            Object d = c.getValue().get("expiryDate");
            return new SimpleStringProperty(d != null
                ? DateUtil.formatDate((java.time.LocalDate) d) : "—");
        });
        exColDaysLeft.setCellValueFactory(c ->
            new SimpleStringProperty(String.valueOf(c.getValue().get("daysLeft")) + "d"));
        exColDaysLeft.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                int days = Integer.parseInt(v.replace("d", ""));
                setStyle(days <= 7
                    ? "-fx-text-fill:-pos-danger; -fx-font-weight:bold;"
                    : "-fx-text-fill:-pos-warning;");
            }
        });
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @FXML
    public void refreshDashboard() {
        // Load data off FX thread; update UI back on it
        new Thread(() -> {
            try {
                DashboardStats stats  = dashboardService.loadStats();
                List<Product>  lowStock = dashboardService.getLowStockProducts();
                List<Map<String, Object>> recentBills = dashboardService.getRecentBills(15);
                List<Map<String, Object>> expiring    = dashboardService.getExpiringProducts(expiryDays);
                List<Map<String, Object>> sessions    = dashboardService.getActiveSessions();

                Platform.runLater(() -> {
                    updateKpis(stats);
                    updateChart(stats.getSalesByHour());
                    updateMachineStatus(sessions);
                    updateLowStockTable(lowStock);
                    updateRecentBillsTable(recentBills);
                    updateExpiryTable(expiring);
                    logger.debug("Dashboard refreshed");
                });
            } catch (Exception e) {
                logger.error("Dashboard refresh error: {}", e.getMessage(), e);
                Platform.runLater(() ->
                    AlertUtil.showWarning("Refresh Warning",
                        "Some dashboard data could not be loaded: " + e.getMessage()));
            }
        }, "dashboard-refresh").start();
    }

    // ── KPI Cards ─────────────────────────────────────────────────────────────

    private void updateKpis(DashboardStats s) {
        kpiSalesLabel.setText(CurrencyUtil.format(s.getTodaySales()));
        kpiBillsLabel.setText(String.valueOf(s.getTodayBillCount()));
        kpiAvgLabel.setText(CurrencyUtil.format(s.getTodayAvgBill()));
        kpiProfitLabel.setText(CurrencyUtil.format(s.getTodayProfit()));
        kpiMarginLabel.setText(s.getTodayProfitPct().toPlainString() + "% margin");

        // Sales delta badge
        BigDecimal salesDelta = s.getSalesDelta();
        String salesSign = salesDelta.compareTo(BigDecimal.ZERO) >= 0 ? "↑ " : "↓ ";
        kpiSalesDeltaLabel.setText(salesSign + CurrencyUtil.format(salesDelta.abs()) + " vs yesterday");
        kpiSalesDeltaLabel.setStyle("-fx-font-size:11px; -fx-text-fill:" +
            (salesDelta.compareTo(BigDecimal.ZERO) >= 0 ? "-pos-success;" : "-pos-danger;"));

        // Bills delta badge
        int billsDelta = s.getBillsDelta();
        kpiBillsDeltaLabel.setText((billsDelta >= 0 ? "↑ " : "↓ ") +
            Math.abs(billsDelta) + " vs yesterday");
        kpiBillsDeltaLabel.setStyle("-fx-font-size:11px; -fx-text-fill:" +
            (billsDelta >= 0 ? "-pos-success;" : "-pos-danger;"));

        // Active cashiers
        activeCashiersLabel.setText(s.getActiveCashierCount() + " active");

        // Alert counts
        lowStockCountLabel.setText(s.getLowStockCount() + " products");
        expiryCountLabel.setText(s.getExpiringCount() + " products");
    }

    // ── Chart ─────────────────────────────────────────────────────────────────

    private void updateChart(Map<String, BigDecimal> byHour) {
        salesChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Sales");

        // Only show hours 6:00 – 22:00 to keep chart readable
        byHour.forEach((hour, amount) -> {
            int h = Integer.parseInt(hour.split(":")[0]);
            if (h >= 6 && h <= 22) {
                XYChart.Data<String, Number> data = new XYChart.Data<>(hour, amount);
                series.getData().add(data);
            }
        });

        salesChart.getData().add(series);

        // Style bars after adding
        Platform.runLater(() -> {
            series.getData().forEach(data -> {
                if (data.getNode() != null) {
                    data.getNode().setStyle(
                        "-fx-bar-fill: -pos-primary; -fx-background-radius:4 4 0 0;");
                }
            });
        });
    }

    @FXML
    private void onChartDateChanged() {
        String sel = chartDatePicker.getValue();
        if (sel == null) return;
        LocalDate date = switch (sel) {
            case "Yesterday" -> LocalDate.now().minusDays(1);
            default          -> LocalDate.now();
        };
        new Thread(() -> {
            Map<String, BigDecimal> data = dashboardService.getSalesByHour(date);
            Platform.runLater(() -> updateChart(data));
        }).start();
    }

    // ── Machine Status ────────────────────────────────────────────────────────

    private void updateMachineStatus(List<Map<String, Object>> sessions) {
        machineStatusBox.getChildren().clear();
        activeCashiersLabel.setText(sessions.size() + " active");

        if (sessions.isEmpty()) {
            Label none = new Label("No active cashier sessions");
            none.setStyle("-fx-text-fill:-pos-text-secondary; -fx-font-size:12px;");
            machineStatusBox.getChildren().add(none);
            return;
        }

        for (Map<String, Object> s : sessions) {
            VBox card = new VBox(4);
            card.setStyle("-fx-background-color:-pos-surface-alt; -fx-background-radius:8; " +
                          "-fx-border-color:-pos-border; -fx-border-radius:8; -fx-padding:10;");

            HBox headerRow = new HBox(6);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            Label dot = new Label("●");
            dot.setStyle("-fx-text-fill:-pos-success; -fx-font-size:10px;");
            Label name = new Label((String) s.getOrDefault("cashierName", "Unknown"));
            name.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label time = new Label("Since " + s.getOrDefault("startTime", "—"));
            time.setStyle("-fx-font-size:11px; -fx-text-fill:-pos-text-secondary;");
            headerRow.getChildren().addAll(dot, name, spacer, time);

            HBox statsRow = new HBox(16);
            statsRow.setAlignment(Pos.CENTER_LEFT);

            Object sales = s.get("shiftSales");
            Object bills = s.get("shiftBills");
            String machineName = (String) s.getOrDefault("machineName", "—");

            Label salesLbl = new Label("Sales: " + (sales != null
                ? CurrencyUtil.format((BigDecimal) sales) : "Rs. 0.00"));
            salesLbl.setStyle("-fx-font-size:12px; -fx-text-fill:-pos-primary;");

            Label billsLbl = new Label("Bills: " + (bills != null ? bills : 0));
            billsLbl.setStyle("-fx-font-size:12px; -fx-text-fill:-pos-text-secondary;");

            Label machineLbl = new Label("📟 " + machineName);
            machineLbl.setStyle("-fx-font-size:11px; -fx-text-fill:-pos-text-secondary;");

            statsRow.getChildren().addAll(salesLbl, billsLbl, machineLbl);
            card.getChildren().addAll(headerRow, statsRow);
            machineStatusBox.getChildren().add(card);
        }
    }

    // ── Low Stock Table ───────────────────────────────────────────────────────

    private void updateLowStockTable(List<Product> products) {
        lowStockTable.setItems(FXCollections.observableArrayList(products));
        lowStockCountLabel.setText(products.size() + " product" + (products.size() == 1 ? "" : "s"));
        lowStockCountLabel.setStyle(products.isEmpty()
            ? "-fx-text-fill:-pos-success;"
            : "-fx-text-fill:-pos-warning; -fx-font-weight:bold;");
    }

    // ── Recent Bills Table ────────────────────────────────────────────────────

    private void updateRecentBillsTable(List<Map<String, Object>> bills) {
        recentBillsTable.setItems(FXCollections.observableArrayList(bills));
    }

    // ── Expiry Table ──────────────────────────────────────────────────────────

    private void updateExpiryTable(List<Map<String, Object>> items) {
        expiryTable.setItems(FXCollections.observableArrayList(items));
        expiryCountLabel.setText(items.size() + " product" + (items.size() == 1 ? "" : "s"));
        expiryCountLabel.setStyle(items.isEmpty()
            ? "-fx-text-fill:-pos-success;"
            : "-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
    }

    @FXML
    private void onExpiryDaysChanged() {
        String sel = expiryDaysCombo.getValue();
        if (sel == null) return;
        expiryDays = switch (sel) {
            case "Expiring in 7 days"  -> 7;
            case "Expiring in 14 days" -> 14;
            case "Expiring in 60 days" -> 60;
            default                    -> 30;
        };
        new Thread(() -> {
            List<Map<String, Object>> data = dashboardService.getExpiringProducts(expiryDays);
            Platform.runLater(() -> updateExpiryTable(data));
        }).start();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML private void showDashboard()        { setActiveNav(navDashboard); }

    @FXML private void navigateToUsers()      {
        setActiveNav(navUsers);
        SceneManager.navigateTo("admin/UserManagement.fxml");
    }
    @FXML private void navigateToProducts()   {
        setActiveNav(navProducts);
        SceneManager.navigateTo("admin/ProductManagement.fxml");
    }
    @FXML private void navigateToCustomers()  {
        setActiveNav(navCustomers);
        SceneManager.navigateTo("admin/CustomerManagement.fxml");
    }
    @FXML private void navigateToSuppliers()   {
        setActiveNav(navSuppliers);
        SceneManager.navigateTo("admin/SupplierManagement.fxml");
    }
    @FXML private void navigateToBills()      {
        setActiveNav(navBills);
        SceneManager.navigateTo("admin/BillHistory.fxml");
    }
    @FXML private void navigateToStock()      {
        setActiveNav(navStock);
        SceneManager.navigateTo("admin/StockAdjustment.fxml");
    }
    @FXML private void navigateToReports()    {
        setActiveNav(navReports);
        SceneManager.navigateTo("admin/Reports.fxml");
    }
    @FXML private void navigateToAudit()      {
        setActiveNav(navAudit);
        SceneManager.navigateTo("admin/AuditLog.fxml");
    }
    @FXML private void navigateToSettings()   {
        setActiveNav(navSettings);
        SceneManager.navigateTo("admin/Settings.fxml");
    }
@FXML private void navigateToCashierMonitor() {
        setActiveNav(navMonitor);
        SceneManager.navigateTo("admin/CashierMonitor.fxml");
    }
    @FXML private void navigateToPOS() {
        setActiveNav(navPOS);
        SceneManager.navigateTo("cashier/POSTerminal.fxml");
    }

    @FXML
    private void logout() {
        if (AlertUtil.confirm("Logout", "Are you sure you want to logout?")) {
            stopTimelines();
            SessionManager.logout();
            SceneManager.clearStack();
            SceneManager.navigateTo("shared/Login.fxml");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setActiveNav(Button active) {
        Button[] all = { navDashboard, navPOS, navUsers, navProducts, navCustomers,
                         navSuppliers, navBills, navStock, navReports,
                         navAudit, navSettings, navMonitor };
        for (Button b : all) {
            b.getStyleClass().remove("active");
        }
        active.getStyleClass().add("active");
        pageTitleLabel.setText(active.getText().replaceAll("^[^A-Za-z]+\\s*", ""));
    }

    private void stopTimelines() {
        if (clockTimeline != null)       clockTimeline.stop();
        if (autoRefreshTimeline != null) autoRefreshTimeline.stop();
    }

    private String formatPaymentType(String raw) {
        if (raw == null) return "—";
        return switch (raw) {
            case "CASH"         -> "💵 Cash";
            case "CARD"         -> "💳 Card";
            case "MOBILE_MONEY" -> "📱 Mobile";
            case "CREDIT"       -> "📋 Credit";
            default             -> raw;
        };
    }
}
