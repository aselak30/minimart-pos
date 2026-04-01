package com.minimartpos.controller.admin;

import com.minimartpos.security.SessionManager;
import com.minimartpos.service.ExcelService;
import com.minimartpos.service.PdfService;
import com.minimartpos.service.ReportService;
import com.minimartpos.service.SettingsService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reports screen controller.
 *
 * Supports 6 report types:
 *  1. Sales Summary
 *  2. Sales by Cashier
 *  3. Sales by Category
 *  4. Sales by Payment Type
 *  5. Top 20 Products
 *  6. Shift Summary
 *
 * Features:
 *  - Date range picker (defaults to today)
 *  - KPI summary row always shown
 *  - Line chart showing daily trend
 *  - Dynamic TableView columns built per report type
 *  - CSV export skeleton
 */
public class ReportsController implements Initializable {

    private static final Logger logger = LogManager.getLogger(ReportsController.class);

    @FXML private Label       sidebarUserLabel;
    @FXML private Label       sidebarCompanyLabel;
    @FXML private DatePicker  fromDate;
    @FXML private DatePicker  toDate;
    @FXML private ListView<String> reportTypeList;

    // KPI row
    @FXML private Label rptSalesLabel;
    @FXML private Label rptBillsLabel;
    @FXML private Label rptAvgLabel;
    @FXML private Label rptProfitLabel;
    @FXML private Label rptDiscountLabel;

    // Chart
    @FXML private LineChart<String, Number> trendChart;
    @FXML private Label chartTitleLabel;

    // Table
    @FXML private TableView<Map<String, Object>> reportTable;
    @FXML private Label tableTitleLabel;
    @FXML private Label rowCountLabel;

    private final ReportService reportService = new ReportService();
    private final SettingsService settingsService = new SettingsService();

    private static final List<String> REPORT_TYPES = List.of(
        "📊  Sales Summary",
        "👤  Sales by Cashier",
        "📦  Sales by Category",
        "💳  Sales by Payment",
        "🏆  Top Products",
        "🕐  Shift Summary"
    );

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        sidebarCompanyLabel.setText("🛒 " + settingsService.company());

        // Date range: default to this month
        fromDate.setValue(LocalDate.now().withDayOfMonth(1));
        toDate.setValue(LocalDate.now());

        // Report type list
        reportTypeList.setItems(FXCollections.observableArrayList(REPORT_TYPES));
        reportTypeList.getSelectionModel().selectFirst();
        reportTypeList.getSelectionModel().selectedItemProperty().addListener(
            (obs, o, n) -> runReport());

        runReport();
        logger.info("Reports screen initialized");
    }

    // ── Run Report ────────────────────────────────────────────────────────────

    @FXML
    public void runReport() {
        LocalDate from = fromDate.getValue();
        LocalDate to   = toDate.getValue();
        if (from == null || to == null) return;
        if (from.isAfter(to)) {
            AlertUtil.showWarning("Date Range", "From date must be before To date.");
            return;
        }

        int typeIdx = reportTypeList.getSelectionModel().getSelectedIndex();

        new Thread(() -> {
            try {
                // Always load summary KPIs
                Map<String, Object> summary = reportService.getSalesSummary(from, to);
                List<Map<String, Object>> trendData = reportService.getDailyTrend(from, to);

                // Load report-specific data
                List<Map<String, Object>> tableData = switch (typeIdx) {
                    case 1 -> reportService.getSalesByCashier(from, to);
                    case 2 -> reportService.getSalesByCategory(from, to);
                    case 3 -> reportService.getSalesByPaymentType(from, to);
                    case 4 -> reportService.getTopProducts(from, to, 20);
                    case 5 -> reportService.getShiftSummary(from, to);
                    default -> trendData;  // Sales Summary uses trend as table
                };

                Platform.runLater(() -> {
                    updateKpis(summary);
                    updateChart(trendData, from, to);
                    buildTable(typeIdx, tableData);
                });
            } catch (Exception e) {
                logger.error("runReport error: {}", e.getMessage(), e);
                Platform.runLater(() ->
                    AlertUtil.showWarning("Report Error", e.getMessage()));
            }
        }, "report-thread").start();
    }

    // ── KPI Row ───────────────────────────────────────────────────────────────

    private void updateKpis(Map<String, Object> summary) {
        rptSalesLabel.setText(CurrencyUtil.format(getBD(summary, "totalSales")));
        rptBillsLabel.setText(String.valueOf(summary.getOrDefault("billCount", 0)));
        rptAvgLabel.setText(CurrencyUtil.format(getBD(summary, "avgBill")));
        rptProfitLabel.setText(CurrencyUtil.format(getBD(summary, "totalProfit")));
        rptDiscountLabel.setText(CurrencyUtil.format(getBD(summary, "totalDiscount")));
    }

    // ── Chart ─────────────────────────────────────────────────────────────────

    private void updateChart(List<Map<String, Object>> trend, LocalDate from, LocalDate to) {
        trendChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        for (Map<String, Object> row : trend) {
            LocalDate day = (LocalDate) row.get("day");
            BigDecimal sales = (BigDecimal) row.get("sales");
            if (day != null && sales != null) {
                series.getData().add(new XYChart.Data<>(day.format(fmt), sales));
            }
        }
        trendChart.getData().add(series);

        // Style the line
        Platform.runLater(() -> {
            if (!series.getData().isEmpty() && series.getNode() != null) {
                series.getNode().setStyle("-fx-stroke: -pos-primary; -fx-stroke-width: 2;");
            }
        });

        chartTitleLabel.setText("Daily Sales Trend  (" +
            from.format(DateUtil.DISPLAY_DATE) + " – " + to.format(DateUtil.DISPLAY_DATE) + ")");
    }

    // ── Dynamic Table ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void buildTable(int typeIdx, List<Map<String, Object>> data) {
        reportTable.getColumns().clear();
        reportTable.setItems(FXCollections.observableArrayList(data));

        String[] titles = {
            "Daily Sales", "By Cashier", "By Category",
            "By Payment Type", "Top Products", "Shift Summary"
        };
        tableTitleLabel.setText(titles[Math.min(typeIdx, titles.length - 1)]);
        rowCountLabel.setText(data.size() + " rows");

        if (data.isEmpty()) return;

        // Build columns from the keys of the first row
        Map<String, Object> first = data.get(0);
        for (String key : first.keySet()) {
            TableColumn<Map<String, Object>, String> col = new TableColumn<>(formatHeader(key));
            col.setCellValueFactory(c -> {
                Object val = c.getValue().get(key);
                if (val instanceof BigDecimal bd) return new SimpleStringProperty(CurrencyUtil.formatPlain(bd));
                if (val instanceof LocalDate ld)  return new SimpleStringProperty(DateUtil.formatDate(ld));
                if (val instanceof java.time.LocalDateTime ldt)
                    return new SimpleStringProperty(DateUtil.formatDateTime(ldt));
                return new SimpleStringProperty(val != null ? val.toString() : "—");
            });
            col.setPrefWidth(120);
            reportTable.getColumns().add(col);
        }
    }

    private String formatHeader(String key) {
        // camelCase → Title Case
        return key.replaceAll("([A-Z])", " $1")
                  .substring(0, 1).toUpperCase() +
               key.replaceAll("([A-Z])", " $1").substring(1);
    }

    @FXML
    private void exportCsv() {
        LocalDate from = fromDate.getValue();
        LocalDate to   = toDate.getValue();
        if (from == null || to == null) return;

        // Export current report data to Excel
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Report to Excel");
        fc.setInitialFileName("report_" + from + "_to_" + to + ".xlsx");
        fc.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        java.io.File file = fc.showSaveDialog(reportTable.getScene().getWindow());
        if (file == null) return;

        int typeIdx = reportTypeList.getSelectionModel().getSelectedIndex();
        String title = REPORT_TYPES.get(typeIdx).replaceAll("^[^A-Za-z]+\\s*", "");

        new Thread(() -> {
            try {
                List<Map<String, Object>> data = switch (typeIdx) {
                    case 1  -> reportService.getSalesByCashier(from, to);
                    case 2  -> reportService.getSalesByCategory(from, to);
                    case 3  -> reportService.getSalesByPaymentType(from, to);
                    case 4  -> reportService.getTopProducts(from, to, 50);
                    case 5  -> reportService.getShiftSummary(from, to);
                    default -> reportService.getDailyTrend(from, to);
                };
                new ExcelService().exportReport(title, data, file.getAbsolutePath());
                javafx.application.Platform.runLater(() ->
                    AlertUtil.showInfo("Exported", data.size() + " rows exported to:\n" + file.getName()));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                    AlertUtil.showError("Export Failed", e.getMessage()));
            }
        }, "export-thread").start();
    }

    @FXML
    private void exportPdf() {
        LocalDate from = fromDate.getValue();
        LocalDate to   = toDate.getValue();
        if (from == null || to == null) return;

        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Sales Report to PDF");
        fc.setInitialFileName("sales_report_" + from + ".pdf");
        fc.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        java.io.File file = fc.showSaveDialog(reportTable.getScene().getWindow());
        if (file == null) return;

        new Thread(() -> {
            try {
                Map<String, Object> summary = reportService.getSalesSummary(from, to);
                List<Map<String, Object>> daily    = reportService.getDailyTrend(from, to);
                List<Map<String, Object>> cashier  = reportService.getSalesByCashier(from, to);
                new PdfService().generateSalesReport(summary, daily, cashier, from, to, file.getAbsolutePath());
                javafx.application.Platform.runLater(() ->
                    AlertUtil.showInfo("PDF Generated", "Report saved to:\n" + file.getName()));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                    AlertUtil.showError("PDF Failed", e.getMessage()));
            }
        }, "pdf-thread").start();
    }
// ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal getBD(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    @FXML private void navigateToDashboard()      { com.minimartpos.util.SceneManager.navigateTo("admin/AdminDashboard.fxml"); }
    @FXML private void navigateToPOS()            { com.minimartpos.util.SceneManager.navigateTo("cashier/POSTerminal.fxml"); }
    @FXML private void navigateToUsers()          { com.minimartpos.util.SceneManager.navigateTo("admin/UserManagement.fxml"); }
    @FXML private void navigateToProducts()       { com.minimartpos.util.SceneManager.navigateTo("admin/ProductManagement.fxml"); }
    @FXML private void navigateToCustomers()      { com.minimartpos.util.SceneManager.navigateTo("admin/CustomerManagement.fxml"); }
    @FXML private void navigateToSuppliers()      { com.minimartpos.util.SceneManager.navigateTo("admin/SupplierManagement.fxml"); }
    @FXML private void navigateToCashierMonitor() { com.minimartpos.util.SceneManager.navigateTo("admin/CashierMonitor.fxml"); }
    @FXML private void navigateToBills()          { com.minimartpos.util.SceneManager.navigateTo("admin/BillHistory.fxml"); }
    @FXML private void navigateToStock()          { com.minimartpos.util.SceneManager.navigateTo("admin/StockAdjustment.fxml"); }
    @FXML private void navigateToReports()        { com.minimartpos.util.SceneManager.navigateTo("admin/Reports.fxml"); }
    @FXML private void navigateToAudit()          { com.minimartpos.util.SceneManager.navigateTo("admin/AuditLog.fxml"); }
    @FXML private void navigateToSettings()       { com.minimartpos.util.SceneManager.navigateTo("admin/Settings.fxml"); }

}
