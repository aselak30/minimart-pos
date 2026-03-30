package com.minimartpos.controller.cashier;

import com.minimartpos.model.Bill;
import javafx.application.Platform;
import com.minimartpos.model.BillItem;
import com.minimartpos.repository.BillRepository;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.StockService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

/**
 * Controller for the Return / Refund dialog.
 *
 * Flow:
 * 1. Cashier enters original bill number → bill items load
 * 2. Cashier checks items to return and adjusts quantities
 * 3. System calculates refund total
 * 4. Cashier selects reason + refund method
 * 5. On confirm: stock is restored, refund bill created, receipt printed
 */
public class ReturnController implements Initializable {

    private static final Logger logger = LogManager.getLogger(ReturnController.class);

    @FXML
    private TextField billSearchField;
    @FXML
    private Label billInfoLabel;
    @FXML
    private VBox itemsSection;
    @FXML
    private VBox summarySection;
    @FXML
    private TableView<ReturnItem> itemsTable;
    @FXML
    private TableColumn<ReturnItem, String> colSelect;
    @FXML
    private TableColumn<ReturnItem, String> colItem;
    @FXML
    private TableColumn<ReturnItem, String> colQtyOrig;
    @FXML
    private TableColumn<ReturnItem, String> colQtyRet;
    @FXML
    private TableColumn<ReturnItem, String> colUnit;
    @FXML
    private TableColumn<ReturnItem, String> colCondition;
    @FXML
    private TableColumn<ReturnItem, String> colRefund;
    @FXML
    private ComboBox<String> reasonCombo;
    @FXML
    private ComboBox<String> refundMethodCombo;
    @FXML
    private Label returnItemCountLabel;
    @FXML
    private Label refundTotalLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Button processBtn;

    private final BillRepository billRepo = new BillRepository();
    private final StockService stockService = new StockService();

    public enum Mode {
        RETURN, VOID
    }

    private Mode mode = Mode.RETURN;

    private Bill originalBill;
    private final ObservableList<ReturnItem> returnItems = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        reasonCombo.setItems(FXCollections.observableArrayList(
                "Defective / Damaged", "Wrong item delivered", "Customer changed mind",
                "Expired product", "Overcharged", "Other"));
        reasonCombo.getSelectionModel().selectFirst();

        refundMethodCombo.setItems(FXCollections.observableArrayList(
                "Cash Refund", "Store Credit", "Card Refund", "Exchange"));
        refundMethodCombo.getSelectionModel().selectFirst();

        setupTableColumns();
        Platform.runLater(this::showRecentBillsHint);
    }

    private void showRecentBillsHint() {
        billInfoLabel.setText("Tip: Search by Bill #, Customer Name, or Phone.");
    }

    private void setupTableColumns() {
        // Checkbox column
        colSelect.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.selectedProperty().addListener((obs, o, sel) -> {
                    if (!isEmpty()) {
                        getTableView().getItems().get(getIndex()).setSelected(sel);
                        updateRefundTotal();
                    }
                });
            }

            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                ReturnItem ri = getTableView().getItems().get(getIndex());
                cb.setSelected(ri.isSelected());
                setGraphic(cb);
            }
        });

        colItem.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBillItem().getProductName()));

        colQtyOrig.setCellValueFactory(
                c -> new SimpleStringProperty(String.valueOf(c.getValue().getBillItem().getQuantity())));

        // Editable return quantity
        colQtyRet.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReturnQty())));
        colQtyRet.setCellFactory(TextFieldTableCell.forTableColumn());
        colQtyRet.setOnEditCommit(event -> {
            ReturnItem ri = event.getRowValue();
            try {
                BigDecimal qty = new BigDecimal(event.getNewValue().trim());
                BigDecimal max = ri.getBillItem().getQuantity();
                if (qty.compareTo(BigDecimal.ZERO) < 0 || qty.compareTo(max) > 0) {
                    AlertUtil.showWarning("Invalid", "Return quantity must be 0–" + max);
                    qty = ri.getReturnQty();
                }
                ri.setReturnQty(qty);
                ri.setSelected(qty.compareTo(BigDecimal.ZERO) > 0);
            } catch (NumberFormatException e) {
                ri.setReturnQty(ri.getBillItem().getQuantity());
            }
            itemsTable.refresh();
            updateRefundTotal();
        });

        colUnit.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.formatPlain(
                c.getValue().getBillItem().getUnitPrice())));

        // Condition Column (Stock vs Damaged)
        colCondition.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(
                    FXCollections.observableArrayList("Stock", "Damaged"));
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
                    if (!isEmpty()) {
                        getTableView().getItems().get(getIndex()).setCondition(n);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReturnItem ri = getTableView().getItems().get(getIndex());
                    combo.getSelectionModel().select(ri.getCondition());
                    setGraphic(combo);
                }
            }
        });

        colRefund.setCellValueFactory(
                c -> new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getRefundAmount())));
    }

    @FXML
    private void searchBill() {
        String query = billSearchField.getText().trim();
        if (query.isEmpty()) {
            showRecentBills();
            return;
        }
        clearError();

        // Search bills (advanced)
        List<Bill> results = billRepo.searchBillsAdvanced(query);
        if (results.isEmpty()) {
            showError("No bills found matching: " + query
                    + "\nTry searching by product name, date (YYYYMMDD), or amount.");
            billInfoLabel.setText("");
            return;
        }

        if (results.size() > 1) {
            billInfoLabel.setText(results.size() + " matches found. Opening selection...");
            // Show selection dialog
            Bill selected = showBillSelectionDialog(results);
            if (selected == null) {
                billInfoLabel.setText("Selection cancelled.");
                return;
            }
            loadBill(selected);
        } else {
            billInfoLabel.setText("1 match found.");
            loadBill(results.get(0));
        }
    }

    public void loadBill(Bill bill) {
        // Reload full bill with items
        Optional<Bill> fullBill = billRepo.findById(bill.getId());
        if (fullBill.isEmpty()) {
            showError("Could not load bill details.");
            return;
        }
        originalBill = fullBill.get();

        if (originalBill.getStatus() == Bill.Status.VOIDED) {
            showError("Cannot return a voided bill.");
            return;
        }
        if (originalBill.getStatus() == Bill.Status.REFUNDED) {
            showError("This bill has already been refunded.");
            return;
        }

        billInfoLabel.setText("Bill: " + originalBill.getBillNumber() +
                "  |  Date: " + (originalBill.getFinalizedAt() != null
                        ? DateUtil.formatDateTime(originalBill.getFinalizedAt())
                        : "—")
                +
                "  |  Cashier: " + originalBill.getCashierName() +
                "  |  Total: " + CurrencyUtil.format(originalBill.getTotalAmount()));

        // Build return item list (default: return all if VOID mode, else clear)
        returnItems.clear();
        for (BillItem item : originalBill.getItems()) {
            ReturnItem ri = new ReturnItem(item);
            if (mode == Mode.VOID) {
                ri.setReturnQty(item.getQuantity());
                ri.setSelected(true);
            }
            returnItems.add(ri);
        }
        itemsTable.setItems(returnItems);
        itemsSection.setVisible(true);
        itemsSection.setManaged(true);
        summarySection.setVisible(true);
        summarySection.setManaged(true);

        if (mode == Mode.VOID) {
            processItemLabel.setText("STEP 2 — Verify Items to Void");
            processBtn.setText("✔ Confirm Void");
            reasonCombo.setDisable(true); // Void reason usually handled separately or inferred
            refundMethodCombo.setDisable(true); // No refund method for void usually (full reversal)
        } else {
            processItemLabel.setText("STEP 2 — Select Items to Return");
            processBtn.setText("✔ Process Return");
        }

        processBtn.setDisable(returnItems.isEmpty());
        updateRefundTotal();
    }

    @FXML
    private Label processItemLabel; // Need to add this to FXML or just use generic

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    private Bill showBillSelectionDialog(List<Bill> bills) {
        ChoiceDialog<Bill> dialog = new ChoiceDialog<>(bills.get(0), bills);
        dialog.setTitle("Select Bill");
        dialog.setHeaderText("Multiple bills found. Please select one:");
        dialog.setContentText("Bill:");

        // Custom rendering for the choice dialog
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK)
                return dialog.getSelectedItem();
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    @FXML
    private void showRecentBills() {
        List<Bill> recent = billRepo.findRecent(15);
        if (recent.isEmpty()) {
            AlertUtil.showInfo("Recent Bills", "No recent finalized bills found.");
            return;
        }
        Bill selected = showBillSelectionDialog(recent);
        if (selected != null)
            loadBill(selected);
    }

    private boolean isFullRefund() {
        if (originalBill == null || returnItems.isEmpty())
            return false;
        // If VOID mode, it's always a full refund of status
        if (mode == Mode.VOID)
            return true;

        for (ReturnItem ri : returnItems) {
            if (!ri.isSelected())
                return false;
            // Compare return qty with original qty
            if (ri.getReturnQty().compareTo(ri.getBillItem().getQuantity()) < 0)
                return false;
        }
        return true;
    }

    // ── Selection helpers ─────────────────────────────────────────────────────

    @FXML
    private void selectAll() {
        returnItems.forEach(ri -> {
            ri.setSelected(true);
            ri.setReturnQty(ri.getBillItem().getQuantity());
        });
        itemsTable.refresh();
        updateRefundTotal();
    }

    @FXML
    private void clearSelection() {
        returnItems.forEach(ri -> {
            ri.setSelected(false);
            ri.setReturnQty(BigDecimal.ZERO);
        });
        itemsTable.refresh();
        updateRefundTotal();
    }

    private void updateRefundTotal() {
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (ReturnItem ri : returnItems) {
            if (ri.isSelected() && ri.getReturnQty().compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(ri.getRefundAmount());
                count++;
            }
        }
        returnItemCountLabel.setText(String.valueOf(count));
        refundTotalLabel.setText(CurrencyUtil.format(total));
        processBtn.setDisable(count == 0);
    }

    // ── Process Return ────────────────────────────────────────────────────────

    @FXML
    private void processReturn() {
        clearError();

        List<ReturnItem> toReturn = returnItems.stream()
                .filter(ri -> ri.isSelected() && ri.getReturnQty().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (toReturn.isEmpty()) {
            showError("Please select at least one item to return.");
            return;
        }

        String reason = reasonCombo.getValue();
        String method = refundMethodCombo.getValue();

        BigDecimal refundTotal = toReturn.stream()
                .map(ReturnItem::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String confirmTitle = (mode == Mode.VOID) ? "Confirm Void" : "Confirm Return";
        String confirmMsg = (mode == Mode.VOID)
                ? "Are you sure you want to VOID this bill? This will reverse all stock and mark the bill as voided."
                : "Process return for " + toReturn.size() + " item(s)?\n" +
                        "Refund: " + CurrencyUtil.format(refundTotal) + " via " + method;

        if (!AlertUtil.confirm(confirmTitle, confirmMsg))
            return;

        // Restore stock for each returned item
        int userId = SessionManager.getCurrentUser().getId();
        boolean allStockOk = true;
        for (ReturnItem ri : toReturn) {
            boolean ok;
            if ("Damaged".equals(ri.getCondition())) {
                ok = stockService.addDamagedStock(ri.getBillItem().getProductId(),
                        ri.getReturnQty(), originalBill.getId(), userId);
            } else {
                ok = stockService.addStock(ri.getBillItem().getProductId(),
                        ri.getReturnQty(), originalBill.getId(), userId, "RETURN");
            }
            if (!ok)
                allStockOk = false;
        }

        boolean statusOk = true;
        if (mode == Mode.VOID) {
            originalBill.setStatus(Bill.Status.VOIDED);
            originalBill.setVoidReason(reason);
            statusOk = billRepo.updateBillStatus(originalBill);
        } else {
            if (isFullRefund()) {
                originalBill.setStatus(Bill.Status.REFUNDED);
                statusOk = billRepo.updateBillStatus(originalBill);
            }
        }

        if (!allStockOk || !statusOk) {
            AlertUtil.showWarning("Partial Success", "The operation completed with some errors. " +
                    "Check logs if stock levels look incorrect.");
        }

        // Broadcast sync events
        try {
            com.minimartpos.network.SyncManager.getInstance().notifyStockChanged(0);
            if (mode == Mode.VOID || isFullRefund()) {
                com.minimartpos.network.SyncManager.getInstance().notifyBillVoided(originalBill.getId());

                // Auto-print receipt for absolute confirmation
                new Thread(() -> {
                    new com.minimartpos.hardware.PrinterManager().printReceipt(originalBill);
                }).start();
            }
        } catch (Exception ignored) {
        }

        logger.info("{} processed: bill={} items={} total={} reason={}",
                mode == Mode.VOID ? "Void" : "Return",
                originalBill.getBillNumber(), toReturn.size(), refundTotal, reason);

        AlertUtil.showInfo(mode == Mode.VOID ? "Bill Voided" : "Return Processed",
                (mode == Mode.VOID ? "Bill has been voided successfully." : "Return completed successfully.") +
                        "\nTotal: " + CurrencyUtil.format(refundTotal) +
                        "\nStock has been updated.");

        close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FXML
    private void cancel() {
        close();
    }

    private void close() {
        ((Stage) processBtn.getScene().getWindow()).close();
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

    // ── Inner class ───────────────────────────────────────────────────────────

    public static class ReturnItem {
        private final BillItem billItem;
        private BigDecimal returnQty;
        private String condition = "Stock"; // "Stock" or "Damaged"
        private boolean selected;

        public ReturnItem(BillItem item) {
            this.billItem = item;
            this.returnQty = BigDecimal.ZERO;
            this.selected = false;
        }

        public BillItem getBillItem() {
            return billItem;
        }

        public BigDecimal getReturnQty() {
            return returnQty;
        }

        public void setReturnQty(BigDecimal q) {
            this.returnQty = q;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String c) {
            this.condition = c;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean s) {
            this.selected = s;
        }

        public BigDecimal getRefundAmount() {
            if (!selected || returnQty == null || returnQty.compareTo(BigDecimal.ZERO) <= 0)
                return BigDecimal.ZERO;
            return billItem.getUnitPrice()
                    .multiply(returnQty);
        }
    }
}
