package com.minimartpos.controller.admin;

import com.minimartpos.model.Bill;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.repository.BillRepository;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.BillingService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import com.minimartpos.util.SceneManager;
import com.minimartpos.config.DatabaseConfig;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BillHistoryController implements Initializable {

    private static final Logger logger = LogManager.getLogger(BillHistoryController.class);

    @FXML private Label    sidebarUserLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private Label    rowCountLabel;
    @FXML private Label    totalSalesLabel;

    @FXML private TableView<Map<String, Object>>            billTable;
    @FXML private TableColumn<Map<String,Object>, String>   colBillNum;
    @FXML private TableColumn<Map<String,Object>, String>   colDate;
    @FXML private TableColumn<Map<String,Object>, String>   colCashier;
    @FXML private TableColumn<Map<String,Object>, String>   colCustomer;
    @FXML private TableColumn<Map<String,Object>, String>   colItems;
    @FXML private TableColumn<Map<String,Object>, String>   colTotal;
    @FXML private TableColumn<Map<String,Object>, String>   colPayment;
    @FXML private TableColumn<Map<String,Object>, String>   colStatus;
    @FXML private TableColumn<Map<String,Object>, String>   colActions;

    @FXML private VBox  detailPanel;
    @FXML private Label detailBillLabel;
    @FXML private VBox  detailContent;
    @FXML private Button voidBillBtn;
    @FXML private Button editBillBtn;

    private final BillRepository billRepo     = new BillRepository();
    private final BillingService billingService = new BillingService();
    private final ObservableList<Map<String, Object>> allBills = FXCollections.observableArrayList();
    private FilteredList<Map<String, Object>> filtered;
    private Map<String, Object> selectedBillRow = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        fromDate.setValue(LocalDate.now().minusDays(7));
        toDate.setValue(LocalDate.now());
        statusFilter.setItems(FXCollections.observableArrayList(
            "All", "FINALIZED", "VOIDED", "REFUNDED"));
        statusFilter.getSelectionModel().selectFirst();
        setupColumns();
        loadBills();
    }

    private void setupColumns() {
        colBillNum.setCellValueFactory(c  -> new SimpleStringProperty((String) c.getValue().get("billNumber")));
        colDate.setCellValueFactory(c     -> new SimpleStringProperty((String) c.getValue().get("time")));
        colCashier.setCellValueFactory(c  -> new SimpleStringProperty((String) c.getValue().get("cashier")));
        colCustomer.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().get("customer") != null ? (String) c.getValue().get("customer") : "Walk-in"));
        colItems.setCellValueFactory(c    -> new SimpleStringProperty(
            String.valueOf(c.getValue().getOrDefault("itemCount", "—"))));
        colTotal.setCellValueFactory(c    -> {
            Object v = c.getValue().get("total");
            return new SimpleStringProperty(v instanceof BigDecimal bd ? CurrencyUtil.format(bd) : "—");
        });
        colPayment.setCellValueFactory(c  -> new SimpleStringProperty(
            formatPayType((String) c.getValue().get("paymentType"))));
        colStatus.setCellValueFactory(c   -> new SimpleStringProperty((String) c.getValue().get("status")));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle(v.equals("VOIDED")
                    ? "-fx-text-fill:-pos-danger;" : "-fx-text-fill:-pos-success;");
            }
        });
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("🔍 View");
            {
                viewBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                    "-fx-background-color:-pos-primary; -fx-text-fill:white; -fx-background-radius:4;");
                viewBtn.setOnAction(e -> {
                    selectedBillRow = getTableView().getItems().get(getIndex());
                    showBillDetail(selectedBillRow);
                });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        // Row double-click
        billTable.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    selectedBillRow = row.getItem();
                    showBillDetail(selectedBillRow);
                }
            });
            return row;
        });
    }

    @FXML
    public void loadBills() {
        LocalDate from = fromDate.getValue() != null ? fromDate.getValue() : LocalDate.now().minusDays(7);
        LocalDate to   = toDate.getValue()   != null ? toDate.getValue()   : LocalDate.now();
        new Thread(() -> {
            List<Map<String, Object>> rows = queryBills(from, to);
            Platform.runLater(() -> {
                allBills.setAll(rows);
                filtered = new FilteredList<>(allBills, r -> true);
                billTable.setItems(filtered);
                applyFilters();
            });
        }).start();
    }

    private List<Map<String, Object>> queryBills(LocalDate from, LocalDate to) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT b.id, b.bill_number, b.total_amount, b.payment_type, b.status, " +
            "  b.finalized_at, u.full_name AS cashier_name, " +
            "  c.name AS customer_name, " +
            "  (SELECT COUNT(*) FROM bill_items bi WHERE bi.bill_id=b.id) AS item_count " +
            "FROM bills b " +
            "LEFT JOIN users u    ON b.cashier_id  = u.id " +
            "LEFT JOIN customers c ON b.customer_id = c.id " +
            "WHERE DATE(b.finalized_at) BETWEEN ? AND ? " +
            "ORDER BY b.finalized_at DESC LIMIT 500";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id",          rs.getInt("id"));
                    row.put("billNumber",  rs.getString("bill_number"));
                    row.put("cashier",     rs.getString("cashier_name"));
                    row.put("customer",    rs.getString("customer_name"));
                    row.put("total",       rs.getBigDecimal("total_amount"));
                    row.put("paymentType", rs.getString("payment_type"));
                    row.put("status",      rs.getString("status"));
                    row.put("itemCount",   rs.getInt("item_count"));
                    Timestamp ts = rs.getTimestamp("finalized_at");
                    row.put("time", ts != null ? ts.toLocalDateTime().format(fmt) : "—");
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("queryBills: {}", e.getMessage(), e);
        }
        return list;
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String status = statusFilter.getValue();
        filtered.setPredicate(row -> {
            String bn = (String) row.getOrDefault("billNumber", "");
            String ca = (String) row.getOrDefault("cashier", "");
            boolean matchSearch = search.isEmpty()
                || bn.toLowerCase().contains(search)
                || ca.toLowerCase().contains(search);
            boolean matchStatus = "All".equals(status) || status == null
                || status.equals(row.get("status"));
            return matchSearch && matchStatus;
        });
        rowCountLabel.setText(filtered.size() + " bills");
        // Recalculate total
        BigDecimal total = filtered.stream()
            .filter(r -> "FINALIZED".equals(r.get("status")))
            .map(r -> (BigDecimal) r.getOrDefault("total", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalSalesLabel.setText(CurrencyUtil.format(total));
    }

    @FXML private void onSearchChanged()  { applyFilters(); }
    @FXML private void onFilterChanged()  { applyFilters(); }

    private void showBillDetail(Map<String, Object> row) {
        int billId = (int) row.get("id");
        Optional<Bill> opt = billRepo.findById(billId);
        if (opt.isEmpty()) { AlertUtil.showWarning("Not Found", "Bill details not available."); return; }

        Bill bill = opt.get();
        detailBillLabel.setText("Bill: " + bill.getBillNumber());
        detailContent.getChildren().clear();

        // Header info
        VBox infoCard = new VBox(5);
        infoCard.setStyle("-fx-background-color:-pos-surface-alt; -fx-padding:10; -fx-background-radius:6;");
        addDetailRow(infoCard, "Cashier:",   bill.getCashierName());
        addDetailRow(infoCard, "Customer:",  bill.getCustomerName() != null ? bill.getCustomerName() : "Walk-in");
        addDetailRow(infoCard, "Date:",      bill.getFinalizedAt() != null ? DateUtil.formatDateTime(bill.getFinalizedAt()) : "—");
        addDetailRow(infoCard, "Payment:",   formatPayType(bill.getPaymentType() != null ? bill.getPaymentType().name() : "—"));
        addDetailRow(infoCard, "Status:",    bill.getStatus().name());
        detailContent.getChildren().add(infoCard);

        // Items
        Label itemsHdr = new Label("ITEMS");
        itemsHdr.setStyle("-fx-font-weight:bold; -fx-font-size:11px; -fx-text-fill:-pos-text-secondary; -fx-padding:8 0 4 0;");
        detailContent.getChildren().add(itemsHdr);
        for (Bill.PayType ignored : Bill.PayType.values()) break; // just for type ref
        for (var item : bill.getItems()) {
            HBox itemRow = new HBox(6);
            Label name = new Label(item.getProductName());
            name.setStyle("-fx-font-size:12px;");
            name.setMaxWidth(180);
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label qty   = new Label("x" + item.getQuantity());
            qty.setStyle("-fx-font-size:12px; -fx-text-fill:-pos-text-secondary;");
            Label total = new Label(CurrencyUtil.formatPlain(item.getLineTotal()));
            total.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
            itemRow.getChildren().addAll(name, sp, qty, total);
            detailContent.getChildren().add(itemRow);
        }

        // Totals
        Separator sep = new Separator();
        sep.setStyle("-fx-padding:4 0;");
        detailContent.getChildren().add(sep);
        addDetailRow(detailContent, "Subtotal:", CurrencyUtil.format(bill.getSubtotal()));
        if (bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0)
            addDetailRow(detailContent, "Discount:", "- " + CurrencyUtil.format(bill.getDiscountAmount()));
        if (bill.getTaxAmount().compareTo(BigDecimal.ZERO) > 0)
            addDetailRow(detailContent, "Tax:", CurrencyUtil.format(bill.getTaxAmount()));
        Label totalLbl = new Label(CurrencyUtil.format(bill.getTotalAmount()));
        totalLbl.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:-pos-primary;");
        addDetailRowStyled(detailContent, "TOTAL:", totalLbl);

        voidBillBtn.setDisable(bill.getStatus() == Bill.Status.VOIDED
            || !SessionManager.hasPermission(Permission.VOID_BILL));
        editBillBtn.setDisable(bill.getStatus() == Bill.Status.VOIDED
            || !SessionManager.hasPermission(Permission.EDIT_BILL_AFTER_PRINT));

        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
    }

    private void addDetailRow(Pane parent, String label, String value) {
        HBox row = new HBox();
        Label lbl = new Label(label); lbl.setStyle("-fx-font-size:12px; -fx-text-fill:-pos-text-secondary;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label val = new Label(value); val.setStyle("-fx-font-size:12px;");
        row.getChildren().addAll(lbl, sp, val);
        parent.getChildren().add(row);
    }

    private void addDetailRowStyled(Pane parent, String label, Label valueNode) {
        HBox row = new HBox();
        Label lbl = new Label(label); lbl.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        row.getChildren().addAll(lbl, sp, valueNode);
        parent.getChildren().add(row);
    }

    @FXML
    private void openEditBill() {
        if (selectedBillRow == null) return;
        int billId = (int) selectedBillRow.get("id");
        Optional<Bill> opt = billRepo.findById(billId);
        if (opt.isEmpty()) { AlertUtil.showWarning("Not Found", "Bill details not available."); return; }
        Bill bill = opt.get();

        if (bill.getStatus() == Bill.Status.VOIDED) {
            AlertUtil.showWarning("Cannot Edit", "Voided bills cannot be edited."); return;
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/admin/EditBill.fxml"));
            javafx.scene.Parent root = loader.load();
            EditBillController ctrl = loader.getController();
            ctrl.setBill(bill);
            ctrl.setOnSaved(() -> {
                loadBills();
                closeDetail();
            });
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Edit Bill — " + bill.getBillNumber());
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setScene(new javafx.scene.Scene(root));
            stage.setResizable(true);
            stage.setMinWidth(740);
            stage.setMinHeight(640);
            stage.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to open edit bill dialog", e);
            AlertUtil.showError("Error", "Could not open edit dialog: " + e.getMessage());
        }
    }
    @FXML
    private void voidSelectedBill() {
        if (selectedBillRow == null) return;
        int billId = (int) selectedBillRow.get("id");
        Optional<Bill> opt = billRepo.findById(billId);
        if (opt.isEmpty()) return;
        String reason = AlertUtil.promptText("Void Bill", "Reason for voiding:", "");
        if (reason.isEmpty()) return;
        BillingService.BillResult result = billingService.voidBill(opt.get(), reason);
        if (result.isSuccess()) {
            AlertUtil.showInfo("Voided", "Bill has been voided.");
            loadBills();
            closeDetail();
        } else {
            AlertUtil.showError("Failed", result.getMessage());
        }
    }

    @FXML
    private void reprintBill() {
        if (selectedBillRow == null) return;
        int billId = (int) selectedBillRow.get("id");
        Optional<Bill> opt = billRepo.findById(billId);
        opt.ifPresent(bill -> new Thread(() -> {
            new com.minimartpos.hardware.PrinterManager().printReceipt(bill);
        }).start());
    }

    @FXML
    private void closeDetail() {
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        selectedBillRow = null;
    }

    private String formatPayType(String raw) {
        if (raw == null) return "—";
        return switch (raw) {
            case "CASH"         -> "💵 Cash";
            case "CARD"         -> "💳 Card";
            case "MOBILE_MONEY" -> "📱 Mobile";
            case "CREDIT"       -> "📋 Credit";
            default             -> raw;
        };
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
