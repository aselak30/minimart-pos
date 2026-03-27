package com.minimartpos.controller.admin;

import com.minimartpos.model.Bill;
import com.minimartpos.network.SyncManager;
import com.minimartpos.network.SyncEvent;
import com.minimartpos.repository.BillRepository;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.CashierMonitorService;
import com.minimartpos.service.CashierMonitorService.CashierSnapshot;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Admin screen — Live Cashier Cash Monitor.
 */
public class CashierMonitorController implements Initializable {

    private static final Logger logger = LogManager.getLogger(CashierMonitorController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter BILL_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int REFRESH_SECONDS = 15;

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label      sidebarUserLabel;
    @FXML private Label      lastRefreshLabel;
    @FXML private Label      autoRefreshLabel;
    @FXML private HBox       alertBanner;
    @FXML private Label      alertBannerText;
    @FXML private Label      activeCashiersKpi;
    @FXML private Label      totalCashKpi;
    @FXML private Label      totalSalesKpi;
    @FXML private Label      totalBillsKpi;
    @FXML private Label      limitAlertsKpi;
    @FXML private FlowPane   cashierCardsPane;
    @FXML private Label      noCashiersLabel;
    // Recent bills table
    @FXML private Button                    pauseBtn;
    @FXML private Label                     recentCountLabel;
    @FXML private TableView<Bill>           recentBillsTable;
    @FXML private TableColumn<Bill, String> rbColTime;
    @FXML private TableColumn<Bill, String> rbColBill;
    @FXML private TableColumn<Bill, String> rbColCashier;
    @FXML private TableColumn<Bill, String> rbColCustomer;
    @FXML private TableColumn<Bill, String> rbColAmount;
    @FXML private TableColumn<Bill, String> rbColPayment;

    private final CashierMonitorService monitorService  = new CashierMonitorService();
    private final BillRepository        billRepository  = new BillRepository();
    private Timeline autoRefreshTimeline;
    private int      countdown = REFRESH_SECONDS;

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        setupRecentBillsTable();

        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdown--;
            autoRefreshLabel.setText("● Auto-refresh: " + countdown + "s");
            if (countdown <= 0) {
                countdown = REFRESH_SECONDS;
                loadData();
            }
        }));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();

        SyncManager.getInstance().addListener(
            SyncEvent.Type.BILL_FINALIZED,
            event -> Platform.runLater(() -> {
                countdown = REFRESH_SECONDS;
                loadData();
            }));

        loadData();
        logger.info("CashierMonitor initialized.");
    }

    // ── Recent Bills Table Setup ───────────────────────────────────────────────

    private void setupRecentBillsTable() {
        if (recentBillsTable == null) return;

        rbColTime.setCellValueFactory(c -> {
            LocalDateTime t = c.getValue().getFinalizedAt() != null
                ? c.getValue().getFinalizedAt() : c.getValue().getCreatedAt();
            return new SimpleStringProperty(t != null ? t.format(BILL_TIME_FMT) : "—");
        });
        rbColBill.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getBillNumber() != null
                ? c.getValue().getBillNumber() : "—"));
        rbColCashier.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCashierName() != null
                ? c.getValue().getCashierName() : "—"));
        rbColCustomer.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCustomerName() != null
                && !c.getValue().getCustomerName().equals("Walk-in")
                ? c.getValue().getCustomerName() : "Walk-in"));
        rbColAmount.setCellValueFactory(c ->
            new SimpleStringProperty(CurrencyUtil.format(c.getValue().getTotalAmount())));
        rbColPayment.setCellValueFactory(c -> {
            Bill.PayType pt = c.getValue().getPaymentType();
            return new SimpleStringProperty(pt != null ? pt.name() : "—");
        });
    }

    private void refreshRecentBills() {
        if (recentBillsTable == null) return;
        try {
            List<Bill> bills = billRepository.findTodayAll();
            recentBillsTable.setItems(FXCollections.observableArrayList(bills));
            if (recentCountLabel != null)
                recentCountLabel.setText(bills.size() + " transaction" + (bills.size() == 1 ? "" : "s") + " today");
        } catch (Exception e) {
            logger.warn("Could not load recent bills: {}", e.getMessage());
        }
    }

    // ── Data Load ─────────────────────────────────────────────────────────────

    @FXML
    public void refreshNow() {
        countdown = REFRESH_SECONDS;
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            List<CashierSnapshot> snapshots = monitorService.getLiveCashierData();
            Platform.runLater(() -> {
                updateUI(snapshots);
                refreshRecentBills();
            });
        }, "cashier-monitor-load").start();
    }

    private void updateUI(List<CashierSnapshot> snapshots) {
        int    active     = 0;
        BigDecimal totalCash  = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;
        int    totalBills = 0;
        int    alerts     = 0;

        for (CashierSnapshot s : snapshots) {
            if (s.getBillCount() > 0 || "OPEN".equals(s.getShiftStatus())) active++;
            totalCash  = totalCash.add(s.getCashCollected());
            totalSales = totalSales.add(s.getTotalSales());
            totalBills += s.getBillCount();
            if (s.isLimitExceeded()) alerts++;
        }

        activeCashiersKpi.setText(String.valueOf(active));
        totalCashKpi.setText(CurrencyUtil.format(totalCash));
        totalSalesKpi.setText(CurrencyUtil.format(totalSales));
        totalBillsKpi.setText(String.valueOf(totalBills));
        limitAlertsKpi.setText(String.valueOf(alerts));
        limitAlertsKpi.setStyle(alerts > 0
            ? "-fx-font-size:26px; -fx-font-weight:bold; -fx-text-fill:-pos-danger;"
            : "-fx-font-size:26px; -fx-font-weight:bold; -fx-text-fill:-pos-text-secondary;");

        if (alerts > 0) {
            StringBuilder msg = new StringBuilder("⚠  CASH LIMIT EXCEEDED: ");
            for (CashierSnapshot s : snapshots) {
                if (s.isLimitExceeded()) {
                    msg.append(s.getCashierName())
                       .append(" (").append(CurrencyUtil.format(s.getCashCollected()))
                       .append(" / ").append(CurrencyUtil.format(s.getCashLimit())).append(")  ");
                }
            }
            alertBannerText.setText(msg.toString().trim());
            alertBanner.setVisible(true);
            alertBanner.setManaged(true);
        } else {
            alertBanner.setVisible(false);
            alertBanner.setManaged(false);
        }

        cashierCardsPane.getChildren().clear();
        boolean anyVisible = false;
        for (CashierSnapshot s : snapshots) {
            cashierCardsPane.getChildren().add(buildCashierCard(s));
            anyVisible = true;
        }
        noCashiersLabel.setVisible(!anyVisible);
        noCashiersLabel.setManaged(!anyVisible);

        lastRefreshLabel.setText("Last refresh: " + LocalDateTime.now().format(TIME_FMT));
    }

    // ── Card Builder ──────────────────────────────────────────────────────────

    private VBox buildCashierCard(CashierSnapshot s) {
        boolean exceeded  = s.isLimitExceeded();
        boolean nearLimit = s.isNearLimit();
        boolean hasShift  = "OPEN".equals(s.getShiftStatus());

        String borderColor = exceeded  ? "-pos-danger"
                           : nearLimit ? "-pos-warning"
                           : "-pos-border";
        String bgColor     = exceeded  ? "#FFF3F3"
                           : nearLimit ? "#FFF8E1"
                           : "white";

        VBox card = new VBox(10);
        card.setPrefWidth(280);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:" + bgColor + "; " +
                      "-fx-border-color:" + borderColor + "; " +
                      "-fx-border-width:2; -fx-border-radius:10; " +
                      "-fx-background-radius:10; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),6,0,0,2);");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(s.getCashierName());
        nameLabel.setStyle("-fx-font-weight:bold; -fx-font-size:14px;");
        nameLabel.setMaxWidth(160);
        Label shiftBadge = new Label(hasShift ? "● SHIFT OPEN" : "○ NO SHIFT");
        shiftBadge.setStyle("-fx-font-size:10px; -fx-font-weight:bold; -fx-padding:2 6; " +
            "-fx-background-radius:8; " +
            (hasShift
                ? "-fx-background-color:#E8F5E9; -fx-text-fill:#2E7D32;"
                : "-fx-background-color:#EEEEEE; -fx-text-fill:#9E9E9E;"));
        Region hSpacer = new Region(); HBox.setHgrow(hSpacer, Priority.ALWAYS);
        header.getChildren().addAll(nameLabel, hSpacer, shiftBadge);
        card.getChildren().add(header);

        VBox cashSection = new VBox(4);
        cashSection.setStyle("-fx-background-color:rgba(0,0,0,0.03); -fx-padding:8; -fx-background-radius:6;");
        HBox cashRow = new HBox();
        Label cashLbl = new Label("💵 Cash Collected");
        cashLbl.setStyle("-fx-font-size:11px; -fx-text-fill:-pos-text-secondary;");
        Region cs = new Region(); HBox.setHgrow(cs, Priority.ALWAYS);
        Label cashAmt = new Label(CurrencyUtil.format(s.getCashCollected()));
        cashAmt.setStyle("-fx-font-size:16px; -fx-font-weight:bold; " +
            (exceeded ? "-fx-text-fill:-pos-danger;" : "-fx-text-fill:-pos-text-primary;"));
        cashRow.getChildren().addAll(cashLbl, cs, cashAmt);
        cashSection.getChildren().add(cashRow);

        if (s.getCashLimit() != null && s.getCashLimit().compareTo(java.math.BigDecimal.ZERO) > 0) {
            ProgressBar bar = new ProgressBar();
            double pct = Math.min(s.getLimitUsedPercent() / 100.0, 1.0);
            bar.setProgress(pct);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setPrefHeight(10);
            String barColor = exceeded  ? "#D32F2F" : nearLimit ? "#F57C00" : "#1976D2";
            bar.setStyle("-fx-accent: " + barColor + ";");

            HBox limitRow = new HBox();
            Label limitLbl = new Label("Limit: " + CurrencyUtil.format(s.getCashLimit()));
            limitLbl.setStyle("-fx-font-size:10px; -fx-text-fill:-pos-text-secondary;");
            Region ls = new Region(); HBox.setHgrow(ls, Priority.ALWAYS);
            BigDecimal remaining = s.getCashRemaining();
            Label remainLbl = new Label(exceeded
                ? "⚠ OVER BY " + CurrencyUtil.format(s.getCashCollected().subtract(s.getCashLimit()))
                : "Remaining: " + CurrencyUtil.format(remaining));
            remainLbl.setStyle("-fx-font-size:10px; -fx-font-weight:bold; " +
                (exceeded  ? "-fx-text-fill:-pos-danger;"
                : nearLimit ? "-fx-text-fill:-pos-warning;"
                : "-fx-text-fill:-pos-success;"));
            limitRow.getChildren().addAll(limitLbl, ls, remainLbl);
            cashSection.getChildren().addAll(bar, limitRow);
        }
        card.getChildren().add(cashSection);

        HBox statsRow = new HBox(16);
        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.getChildren().addAll(
            buildStat("🧾 Bills", String.valueOf(s.getBillCount())),
            buildStat("💳 Total Sales", CurrencyUtil.format(s.getTotalSales())),
            buildStat("💰 Opening", CurrencyUtil.format(s.getOpeningCash()))
        );
        card.getChildren().add(statsRow);

        if (s.getDailyTarget().compareTo(BigDecimal.ZERO) > 0) {
            double targetPct = Math.min(s.getTargetPercent(), 100.0);
            ProgressBar targetBar = new ProgressBar(targetPct / 100.0);
            targetBar.setMaxWidth(Double.MAX_VALUE);
            targetBar.setPrefHeight(8);
            targetBar.setStyle("-fx-accent: #388E3C;");
            HBox targetRow = new HBox();
            Label tLbl = new Label("Target: " + CurrencyUtil.format(s.getDailyTarget()));
            tLbl.setStyle("-fx-font-size:10px; -fx-text-fill:-pos-text-secondary;");
            Region ts = new Region(); HBox.setHgrow(ts, Priority.ALWAYS);
            Label tPct = new Label(String.format("%.0f%%", targetPct));
            tPct.setStyle("-fx-font-size:10px; -fx-font-weight:bold; -fx-text-fill:#388E3C;");
            targetRow.getChildren().addAll(tLbl, ts, tPct);
            Label targetHeader = new Label("📈 Daily Target");
            targetHeader.setStyle("-fx-font-size:10px; -fx-text-fill:-pos-text-secondary;");
            card.getChildren().addAll(targetHeader, targetBar, targetRow);
        }

        if (s.getLastSaleTime() != null) {
            Label lastSale = new Label("Last sale: " + DateUtil.formatDateTime(s.getLastSaleTime()));
            lastSale.setStyle("-fx-font-size:10px; -fx-text-fill:-pos-text-secondary;");
            card.getChildren().add(lastSale);
        }

        if (exceeded) {
            Label alert = new Label("⚠  CASH LIMIT EXCEEDED — Please collect cash");
            alert.setStyle("-fx-text-fill:-pos-danger; -fx-font-weight:bold; -fx-font-size:11px;");
            alert.setWrapText(true);
            card.getChildren().add(alert);
        } else if (nearLimit) {
            Label warn = new Label("⚠  Approaching cash limit");
            warn.setStyle("-fx-text-fill:-pos-warning; -fx-font-weight:bold; -fx-font-size:11px;");
            card.getChildren().add(warn);
        }

        return card;
    }

    private VBox buildStat(String label, String value) {
        VBox box = new VBox(2);
        Label l = new Label(label);
        l.setStyle("-fx-font-size:10px; -fx-text-fill:-pos-text-secondary;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
        box.getChildren().addAll(l, v);
        return box;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FXML
    private void dismissAlert() {
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
    }

    @FXML
    private void toggleAutoRefresh() {
        if (autoRefreshTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            autoRefreshTimeline.stop();
            autoRefreshLabel.setText("⏸ Auto-refresh: paused");
            if (pauseBtn != null) pauseBtn.setText("▶ Resume");
        } else {
            autoRefreshTimeline.play();
            if (pauseBtn != null) pauseBtn.setText("⏸ Pause");
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    @FXML private void navigateToDashboard()      { SceneManager.navigateTo("admin/AdminDashboard.fxml"); }
    @FXML private void navigateToPOS()            { SceneManager.navigateTo("cashier/POSTerminal.fxml"); }
    @FXML private void navigateToUsers()          { SceneManager.navigateTo("admin/UserManagement.fxml"); }
    @FXML private void navigateToProducts()       { SceneManager.navigateTo("admin/ProductManagement.fxml"); }
    @FXML private void navigateToCustomers()      { SceneManager.navigateTo("admin/CustomerManagement.fxml"); }
    @FXML private void navigateToSuppliers()      { SceneManager.navigateTo("admin/SupplierManagement.fxml"); }
    @FXML private void navigateToCashierMonitor() { SceneManager.navigateTo("admin/CashierMonitor.fxml"); }
    @FXML private void navigateToBills()          { SceneManager.navigateTo("admin/BillHistory.fxml"); }
    @FXML private void navigateToStock()          { SceneManager.navigateTo("admin/StockAdjustment.fxml"); }
    @FXML private void navigateToReports()        { SceneManager.navigateTo("admin/Reports.fxml"); }
    @FXML private void navigateToAudit()          { SceneManager.navigateTo("admin/AuditLog.fxml"); }
    @FXML private void navigateToSettings()       { SceneManager.navigateTo("admin/Settings.fxml"); }
}
