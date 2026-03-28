package com.minimartpos.controller.admin;

import com.minimartpos.model.Bill;
import com.minimartpos.model.BillItem;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.repository.BillRepository;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.AuditService;
import com.minimartpos.service.CustomerService;
import com.minimartpos.service.StockService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;


/**
 * Controller for the Edit Bill dialog.
 *
 * Business rules:
 *  - Only FINALIZED bills can be edited (not VOIDED or already REFUNDED)
 *  - Edit window is configurable (default 2 hours from bill finalization)
 *  - Admins bypass the time window restriction
 *  - Requires EDIT_BILL_AFTER_PRINT permission
 *  - A correction reason is mandatory
 *  - All changes are written to the audit log
 *  - Stock is adjusted automatically if quantities changed
 *
 * What can be changed:
 *  ✔ Customer (assign / clear)
 *  ✔ Payment type
 *  ✔ Item quantities (will adjust stock)
 *  ✔ Item unit prices (logs price change)
 *  ✔ Item discount %
 *  ✔ Bill-level discount %
 *  ✔ Remove items (restores stock)
 *
 * What cannot be changed:
 *  ✗ Bill number
 *  ✗ Cashier
 *  ✗ Bill date/time
 *  ✗ Adding completely new products (use Return + new bill)
 */
public class EditBillController implements Initializable {

    private static final Logger logger = LogManager.getLogger(EditBillController.class);

    /** Edit time window in hours (admin bypasses this). */
    private static final int EDIT_WINDOW_HOURS = 2;

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label       headerLabel;
    @FXML private Label       timeRemainingLabel;

    // Info row (read-only)
    @FXML private Label       billNumberLabel;
    @FXML private Label       billDateLabel;
    @FXML private Label       billCashierLabel;
    @FXML private Label       originalTotalLabel;

    // Editable header
    @FXML private TextField   customerField;
    @FXML private ComboBox<String> paymentTypeCombo;

    // Items table
    @FXML private TableView<BillItem>            itemsTable;
    @FXML private TableColumn<BillItem, String>  colProduct;
    @FXML private TableColumn<BillItem, String>  colQty;
    @FXML private TableColumn<BillItem, String>  colPrice;
    @FXML private TableColumn<BillItem, String>  colDisc;
    @FXML private TableColumn<BillItem, String>  colTotal;
    @FXML private TableColumn<BillItem, String>  colRemove;

    // Totals
    @FXML private TextField   billDiscountField;
    @FXML private Label       newSubtotalLabel;
    @FXML private Label       newDiscountLabel;
    @FXML private Label       newTotalLabel;
    @FXML private Label       totalDiffLabel;
    @FXML private HBox        totalDiffRow;

    // Reason + error
    @FXML private TextArea    reasonField;
    @FXML private Label       errorLabel;
    @FXML private Button      saveBtn;

    // ── State ─────────────────────────────────────────────────────────────────
    private Bill                           bill;
    private final ObservableList<BillItem> editableItems = FXCollections.observableArrayList();
    private int                            selectedCustomerId = 0;
    private String                         selectedCustomerName = null;

    private final BillRepository  billRepo       = new BillRepository();
    private final StockService    stockService   = new StockService();
    private final AuditService    auditService   = new AuditService();
    private final CustomerService customerService = new CustomerService();

    /** Called after successful save so BillHistoryController can refresh. */
    private Runnable onSaved;

    private Timeline countdownTimer;

    // ── Public API ────────────────────────────────────────────────────────────

    public void setBill(Bill bill) {
        this.bill = bill;
        loadBill();
    }

    public void setOnSaved(Runnable callback) { this.onSaved = callback; }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        paymentTypeCombo.setItems(FXCollections.observableArrayList(
            "CASH", "CARD", "MOBILE_MONEY", "CREDIT"));
        setupTable();
    }

    private void loadBill() {
        if (bill == null) return;

        headerLabel.setText("✏  Edit Bill  —  " + bill.getBillNumber());
        billNumberLabel.setText(bill.getBillNumber());
        billDateLabel.setText(bill.getFinalizedAt() != null
            ? DateUtil.formatDateTime(bill.getFinalizedAt()) : "—");
        billCashierLabel.setText(bill.getCashierName() != null ? bill.getCashierName() : "—");
        originalTotalLabel.setText(CurrencyUtil.format(bill.getTotalAmount()));

        customerField.setText(bill.getCustomerName() != null
            ? bill.getCustomerName() : "Walk-in");
        selectedCustomerId   = bill.getCustomerId();
        selectedCustomerName = bill.getCustomerName();

        paymentTypeCombo.setValue(bill.getPaymentType() != null
            ? bill.getPaymentType().name() : "CASH");

        billDiscountField.setText(bill.getDiscountPercent() != null
            ? bill.getDiscountPercent().toPlainString() : "0");

        // Deep-copy items so we don't mutate the original
        editableItems.clear();
        for (BillItem item : bill.getItems()) {
            BillItem copy = copyItem(item);
            editableItems.add(copy);
        }
        itemsTable.setItems(editableItems);

        // Check time window
        checkEditWindow();

        // Start countdown display
        startCountdown();
        recalculate();
    }

    private void checkEditWindow() {
        boolean isAdmin = SessionManager.getCurrentUser()
            .getRole() == com.minimartpos.model.enums.Role.ADMIN ||
            SessionManager.getCurrentUser().getRole() == com.minimartpos.model.enums.Role.SUPER_ADMIN;
        if (isAdmin) {
            timeRemainingLabel.setText("Admin — no time limit");
            return;
        }
        if (!SessionManager.hasPermission(Permission.EDIT_BILL_AFTER_PRINT)) {
            showError("You do not have permission to edit bills.");
            saveBtn.setDisable(true);
            return;
        }
        if (bill.getFinalizedAt() != null) {
            long hoursAgo = ChronoUnit.HOURS.between(
                bill.getFinalizedAt(), LocalDateTime.now());
            if (hoursAgo >= EDIT_WINDOW_HOURS) {
                showError("Edit window expired. Bills can only be edited within " +
                          EDIT_WINDOW_HOURS + " hours of finalization.");
                saveBtn.setDisable(true);
            }
        }
    }

    private void startCountdown() {
        if (bill.getFinalizedAt() == null) return;
        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long remaining = ChronoUnit.MINUTES.between(
                LocalDateTime.now(),
                bill.getFinalizedAt().plusHours(EDIT_WINDOW_HOURS));
            if (remaining <= 0) {
                timeRemainingLabel.setText("⚠ Edit window expired");
                timeRemainingLabel.setStyle("-fx-text-fill:-pos-danger;");
                saveBtn.setDisable(true);
                countdownTimer.stop();
            } else {
                timeRemainingLabel.setText("Edit window: " + remaining + " min remaining");
            }
        }));
        countdownTimer.setCycleCount(Timeline.INDEFINITE);
        // Only run countdown for non-admins
        com.minimartpos.model.enums.Role role = SessionManager.getCurrentUser().getRole();
        if (role != com.minimartpos.model.enums.Role.ADMIN && role != com.minimartpos.model.enums.Role.SUPER_ADMIN) {
            countdownTimer.play();
        }
    }

    // ── Table Setup ───────────────────────────────────────────────────────────

    private void setupTable() {
        itemsTable.setEditable(true);
        itemsTable.setItems(editableItems);

        colProduct.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getProductName()));

        // Editable: Quantity
        colQty.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getQuantity().toString()));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn());
        colQty.setOnEditCommit(e -> {
            BillItem item = e.getRowValue();
            try {
                BigDecimal qty = new BigDecimal(e.getNewValue().trim());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) { AlertUtil.showWarning("Invalid", "Quantity must be greater than 0."); return; }
                item.setQuantity(qty);
                recalcItemTotal(item);
                itemsTable.refresh();
                recalculate();
            } catch (NumberFormatException ex) {
                AlertUtil.showWarning("Invalid", "Please enter a valid number.");
            }
        });

        // Editable: Unit Price
        colPrice.setCellValueFactory(c ->
            new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getUnitPrice())));
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn());
        colPrice.setOnEditCommit(e -> {
            BillItem item = e.getRowValue();
            BigDecimal price = CurrencyUtil.parse(e.getNewValue());
            if (!price.equals(BigDecimal.ZERO)) {
                item.setUnitPrice(price);
                recalcItemTotal(item);
                itemsTable.refresh();
                recalculate();
            }
        });

        // Editable: Discount %
        colDisc.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getDiscountPercent().toPlainString()));
        colDisc.setCellFactory(TextFieldTableCell.forTableColumn());
        colDisc.setOnEditCommit(e -> {
            BillItem item = e.getRowValue();
            try {
                BigDecimal disc = new BigDecimal(e.getNewValue().trim());
                if (disc.compareTo(BigDecimal.ZERO) < 0 ||
                        disc.compareTo(BigDecimal.valueOf(100)) > 0) {
                    AlertUtil.showWarning("Invalid", "Discount must be 0–100%."); return;
                }
                item.setDiscountPercent(disc);
                recalcItemTotal(item);
                itemsTable.refresh();
                recalculate();
            } catch (NumberFormatException ex) {
                AlertUtil.showWarning("Invalid", "Enter a number 0–100.");
            }
        });

        // Computed: Line total
        colTotal.setCellValueFactory(c ->
            new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getLineTotal())));

        // Remove item button
        colRemove.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✖");
            {
                btn.setStyle("-fx-background-color:#FFEBEE; -fx-text-fill:-pos-danger; " +
                    "-fx-padding:2 6; -fx-cursor:hand; -fx-background-radius:4;");
                btn.setOnAction(e -> {
                    if (editableItems.size() <= 1) {
                        AlertUtil.showWarning("Cannot Remove",
                            "A bill must have at least one item. Void the bill instead.");
                        return;
                    }
                    BillItem item = getTableView().getItems().get(getIndex());
                    if (AlertUtil.confirm("Remove Item",
                            "Remove '" + item.getProductName() + "' from this bill?")) {
                        editableItems.remove(item);
                        recalculate();
                    }
                });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    // ── Calculations ──────────────────────────────────────────────────────────

    @FXML
    public void recalculate() {
        // Recalculate each item total
        for (BillItem item : editableItems) {
            recalcItemTotal(item);
        }
        itemsTable.refresh();

        // Subtotal
        BigDecimal subtotal = editableItems.stream()
            .map(BillItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Bill discount
        BigDecimal discPct = BigDecimal.ZERO;
        try {
            String raw = billDiscountField.getText().trim();
            if (!raw.isEmpty()) discPct = new BigDecimal(raw);
        } catch (NumberFormatException ignored) {}

        BigDecimal discAmt = subtotal.multiply(discPct)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal newTotal = subtotal.subtract(discAmt);

        newSubtotalLabel.setText(CurrencyUtil.format(subtotal));
        newDiscountLabel.setText("- " + CurrencyUtil.format(discAmt));
        newTotalLabel.setText(CurrencyUtil.format(newTotal));

        // Difference vs original
        BigDecimal diff = newTotal.subtract(bill.getTotalAmount());
        String diffSign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        totalDiffLabel.setText(diffSign + CurrencyUtil.format(diff));
        totalDiffLabel.setStyle(diff.compareTo(BigDecimal.ZERO) > 0
            ? "-fx-text-fill:-pos-danger; -fx-font-size:12px;"
            : diff.compareTo(BigDecimal.ZERO) < 0
                ? "-fx-text-fill:-pos-success; -fx-font-size:12px;"
                : "-fx-text-fill:-pos-text-secondary; -fx-font-size:12px;");
    }

    private void recalcItemTotal(BillItem item) {
        BigDecimal baseQty = item.isWeightBased() ? item.getWeight() : item.getQuantity();
        if (baseQty == null) baseQty = BigDecimal.ZERO;
        BigDecimal gross = item.getUnitPrice()
            .multiply(baseQty);
        BigDecimal discAmt = gross.multiply(item.getDiscountPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        item.setDiscountAmount(discAmt);
        item.setLineTotal(gross.subtract(discAmt));
    }

    // ── Customer ──────────────────────────────────────────────────────────────

    @FXML
    private void selectCustomer() {
        String name = AlertUtil.promptText("Select Customer",
            "Enter customer name or phone to search:", "");
        if (name.isEmpty()) return;
        var results = customerService.search(name);
        if (results.isEmpty()) {
            AlertUtil.showInfo("Not Found",
                "No customer found. You can add them in Customer Management.");
            return;
        }
        if (results.size() == 1) {
            selectedCustomerId   = results.get(0).getId();
            selectedCustomerName = results.get(0).getName();
            customerField.setText(selectedCustomerName);
        } else {
            List<String> names = new java.util.ArrayList<>();
            results.forEach(c -> names.add(c.getName() + " (" + c.getPhone() + ")"));
            ChoiceDialog<String> d = new ChoiceDialog<>(names.get(0), names);
            d.setTitle("Select Customer"); d.setHeaderText(null);
            d.setContentText("Choose:");
            d.showAndWait().ifPresent(sel -> {
                int idx = names.indexOf(sel);
                selectedCustomerId   = results.get(idx).getId();
                selectedCustomerName = results.get(idx).getName();
                customerField.setText(selectedCustomerName);
            });
        }
    }

    @FXML
    private void clearCustomer() {
        selectedCustomerId   = 0;
        selectedCustomerName = null;
        customerField.setText("Walk-in");
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @FXML
    private void saveChanges() {
        clearError();

        // Validation
        String reason = reasonField.getText().trim();
        if (reason.isEmpty()) {
            showError("A correction reason is required.");
            reasonField.requestFocus();
            return;
        }
        if (editableItems.isEmpty()) {
            showError("Bill must have at least one item.");
            return;
        }

        // Build the updated bill
        Bill updated = bill; // mutate in place (already loaded)

        // Snapshot old stock quantities for adjustment
        List<BillItem> oldItems   = new ArrayList<>(bill.getItems());
        List<BillItem> newItems   = new ArrayList<>(editableItems);

        // Recalculate totals
        recalculate();
        BigDecimal subtotal  = newItems.stream()
            .map(BillItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discPct   = BigDecimal.ZERO;
        try { discPct = new BigDecimal(billDiscountField.getText().trim()); }
        catch (NumberFormatException ignored) {}
        BigDecimal discAmt   = subtotal.multiply(discPct)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal newTotal  = subtotal.subtract(discAmt);

        updated.setCustomerId(selectedCustomerId);
        updated.setCustomerName(selectedCustomerName != null
            ? selectedCustomerName : "Walk-in");
        updated.setPaymentType(Bill.PayType.valueOf(
            paymentTypeCombo.getValue() != null
                ? paymentTypeCombo.getValue() : "CASH"));
        updated.setSubtotal(subtotal);
        updated.setDiscountPercent(discPct);
        updated.setDiscountAmount(discAmt);
        updated.setTotalAmount(newTotal);
        updated.setItems(newItems);
        updated.setNotes("EDITED: " + reason +
            " | By: " + SessionManager.getCurrentUser().getUsername() +
            " | At: " + DateUtil.formatDateTime(LocalDateTime.now()) +
            (updated.getNotes() != null ? " | " + updated.getNotes() : ""));

        // Confirm if total changed significantly
        BigDecimal diff = newTotal.subtract(bill.getTotalAmount()).abs();
        if (diff.compareTo(BigDecimal.valueOf(100)) > 0) {
            if (!AlertUtil.confirm("Large Change",
                    "The total is changing by " + CurrencyUtil.format(diff) +
                    ". Continue?")) return;
        }

        // Save to DB
        boolean saved = billRepo.updateBill(updated);
        if (!saved) {
            showError("Failed to save changes. Please try again.");
            return;
        }

        // ── Adjust stock for quantity changes ──────────────────────────────────
        adjustStock(oldItems, newItems, updated.getId());

        // ── Audit ──────────────────────────────────────────────────────────────
        auditService.log("BILL_EDITED", "bills", updated.getId(),
            "total=" + bill.getTotalAmount(), "total=" + newTotal + " reason=" + reason);

        logger.info("Bill {} edited by {}: {} → {} ({})",
            updated.getBillNumber(),
            SessionManager.getCurrentUser().getUsername(),
            bill.getTotalAmount(), newTotal, reason);

        if (countdownTimer != null) countdownTimer.stop();
        AlertUtil.showInfo("Saved", "Bill " + updated.getBillNumber() + " updated.");
        if (onSaved != null) onSaved.run();
        close();
    }

    /**
     * Re-adjusts stock for changed/removed items:
     *  - Restore old quantity for each old item
     *  - Deduct new quantity for each new item
     *  Only adjusts when quantities actually differ.
     */
    private void adjustStock(List<BillItem> oldItems,
                              List<BillItem> newItems, int billId) {
        int userId = SessionManager.getCurrentUser().getId();

        // Build map: productId → new qty
        java.util.Map<Integer, BigDecimal> newQtyMap = new java.util.HashMap<>();
        for (BillItem ni : newItems) newQtyMap.put(ni.getProductId(), ni.getQuantity());

        for (BillItem old : oldItems) {
            int pid    = old.getProductId();
            BigDecimal oldQty = old.getQuantity();
            BigDecimal newQty = newQtyMap.getOrDefault(pid, BigDecimal.ZERO); // 0 = item was removed

            if (oldQty != null && newQty != null && oldQty.compareTo(newQty) == 0) continue; // unchanged

            // Restore old deduction
            if (oldQty != null) {
                stockService.addStock(pid, oldQty, billId, userId, "BILL_EDIT_RESTORE");
            }

            // Deduct new quantity (if item still present)
            if (newQty != null && newQty.compareTo(BigDecimal.ZERO) > 0) {
                stockService.deductStock(pid, newQty, billId, userId);
            }

            logger.debug("Stock adjusted for product {}: {} → {}", pid, oldQty, newQty);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BillItem copyItem(BillItem src) {
        BillItem copy = new BillItem();
        copy.setId(src.getId());
        copy.setBillId(src.getBillId());
        copy.setProductId(src.getProductId());
        copy.setProductName(src.getProductName());
        copy.setProductBarcode(src.getProductBarcode());
        copy.setQuantity(src.getQuantity());
        copy.setUnitPrice(src.getUnitPrice());
        copy.setOriginalPrice(src.getOriginalPrice());
        copy.setCostPrice(src.getCostPrice());
        copy.setDiscountPercent(src.getDiscountPercent() != null
            ? src.getDiscountPercent() : BigDecimal.ZERO);
        copy.setDiscountAmount(src.getDiscountAmount() != null
            ? src.getDiscountAmount() : BigDecimal.ZERO);
        copy.setTaxRate(src.getTaxRate());
        copy.setTaxAmount(src.getTaxAmount());
        copy.setLineTotal(src.getLineTotal());
        copy.setWeightBased(src.isWeightBased());
        copy.setWeight(src.getWeight());
        copy.setWeightUnit(src.getWeightUnit());
        return copy;
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    @FXML
    private void cancel() {
        if (countdownTimer != null) countdownTimer.stop();
        close();
    }

    private void close() {
        ((Stage) saveBtn.getScene().getWindow()).close();
    }
}
