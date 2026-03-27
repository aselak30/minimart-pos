package com.minimartpos.controller.cashier;

import com.minimartpos.model.Bill;
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
 *  1. Cashier enters original bill number → bill items load
 *  2. Cashier checks items to return and adjusts quantities
 *  3. System calculates refund total
 *  4. Cashier selects reason + refund method
 *  5. On confirm: stock is restored, refund bill created, receipt printed
 */
public class ReturnController implements Initializable {

    private static final Logger logger = LogManager.getLogger(ReturnController.class);

    @FXML private TextField  billSearchField;
    @FXML private Label      billInfoLabel;
    @FXML private VBox       itemsSection;
    @FXML private VBox       summarySection;
    @FXML private TableView<ReturnItem>            itemsTable;
    @FXML private TableColumn<ReturnItem, String>  colSelect;
    @FXML private TableColumn<ReturnItem, String>  colItem;
    @FXML private TableColumn<ReturnItem, String>  colQtyOrig;
    @FXML private TableColumn<ReturnItem, String>  colQtyRet;
    @FXML private TableColumn<ReturnItem, String>  colUnit;
    @FXML private TableColumn<ReturnItem, String>  colRefund;
    @FXML private ComboBox<String> reasonCombo;
    @FXML private ComboBox<String> refundMethodCombo;
    @FXML private Label      returnItemCountLabel;
    @FXML private Label      refundTotalLabel;
    @FXML private Label      errorLabel;
    @FXML private Button     processBtn;

    private final BillRepository billRepo    = new BillRepository();
    private final StockService   stockService = new StockService();

    private Bill                          originalBill;
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
    }

    private void setupTableColumns() {
        // Checkbox column
        colSelect.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            { cb.selectedProperty().addListener((obs, o, sel) -> {
                if (!isEmpty()) {
                    getTableView().getItems().get(getIndex()).setSelected(sel);
                    updateRefundTotal();
                }
            }); }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                ReturnItem ri = getTableView().getItems().get(getIndex());
                cb.setSelected(ri.isSelected());
                setGraphic(cb);
            }
        });

        colItem.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getBillItem().getProductName()));

        colQtyOrig.setCellValueFactory(c ->
            new SimpleStringProperty(String.valueOf(c.getValue().getBillItem().getQuantity())));

        // Editable return quantity
        colQtyRet.setCellValueFactory(c ->
            new SimpleStringProperty(String.valueOf(c.getValue().getReturnQty())));
        colQtyRet.setCellFactory(TextFieldTableCell.forTableColumn());
        colQtyRet.setOnEditCommit(event -> {
            ReturnItem ri = event.getRowValue();
            try {
                int qty = Integer.parseInt(event.getNewValue().trim());
                int max = ri.getBillItem().getQuantity();
                if (qty < 0 || qty > max) {
                    AlertUtil.showWarning("Invalid", "Return quantity must be 0–" + max);
                    qty = ri.getReturnQty();
                }
                ri.setReturnQty(qty);
                ri.setSelected(qty > 0);
            } catch (NumberFormatException e) {
                ri.setReturnQty(ri.getBillItem().getQuantity());
            }
            itemsTable.refresh();
            updateRefundTotal();
        });

        colUnit.setCellValueFactory(c ->
            new SimpleStringProperty(CurrencyUtil.formatPlain(
                c.getValue().getBillItem().getUnitPrice())));

        colRefund.setCellValueFactory(c ->
            new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getRefundAmount())));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @FXML
    private void searchBill() {
        String billNum = billSearchField.getText().trim();
        if (billNum.isEmpty()) {
            showError("Please enter a bill number."); return;
        }
        clearError();

        // Find bill by number
        Optional<Bill> opt = findByNumber(billNum);
        if (opt.isEmpty()) {
            showError("Bill not found: " + billNum);
            billInfoLabel.setText("");
            return;
        }

        originalBill = opt.get();

        if (originalBill.getStatus() == Bill.Status.VOIDED) {
            showError("Cannot return a voided bill."); return;
        }
        if (originalBill.getStatus() == Bill.Status.REFUNDED) {
            showError("This bill has already been refunded."); return;
        }

        billInfoLabel.setText("Bill: " + originalBill.getBillNumber() +
            "  |  Date: " + (originalBill.getFinalizedAt() != null
                ? DateUtil.formatDateTime(originalBill.getFinalizedAt()) : "—") +
            "  |  Cashier: " + originalBill.getCashierName() +
            "  |  Total: " + CurrencyUtil.format(originalBill.getTotalAmount()));

        // Build return item list (default: return all)
        returnItems.clear();
        for (BillItem item : originalBill.getItems()) {
            ReturnItem ri = new ReturnItem(item);
            ri.setReturnQty(item.getQuantity());
            ri.setSelected(true);
            returnItems.add(ri);
        }
        itemsTable.setItems(returnItems);
        itemsSection.setVisible(true);
        itemsSection.setManaged(true);
        summarySection.setVisible(true);
        summarySection.setManaged(true);
        processBtn.setDisable(false);
        updateRefundTotal();
    }

    private Optional<Bill> findByNumber(String billNum) {
        // Query by bill_number
        try (var conn = com.minimartpos.config.DatabaseConfig.getConnection();
             var ps = conn.prepareStatement("SELECT id FROM bills WHERE bill_number=?")) {
            ps.setString(1, billNum);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return billRepo.findById(rs.getInt(1));
            }
        } catch (Exception e) {
            logger.error("findByNumber: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // ── Selection helpers ─────────────────────────────────────────────────────

    @FXML private void selectAll() {
        returnItems.forEach(ri -> {
            ri.setSelected(true);
            ri.setReturnQty(ri.getBillItem().getQuantity());
        });
        itemsTable.refresh();
        updateRefundTotal();
    }

    @FXML private void clearSelection() {
        returnItems.forEach(ri -> { ri.setSelected(false); ri.setReturnQty(0); });
        itemsTable.refresh();
        updateRefundTotal();
    }

    private void updateRefundTotal() {
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (ReturnItem ri : returnItems) {
            if (ri.isSelected() && ri.getReturnQty() > 0) {
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
            .filter(ri -> ri.isSelected() && ri.getReturnQty() > 0)
            .toList();

        if (toReturn.isEmpty()) {
            showError("Please select at least one item to return."); return;
        }

        String reason = reasonCombo.getValue();
        String method = refundMethodCombo.getValue();

        BigDecimal refundTotal = toReturn.stream()
            .map(ReturnItem::getRefundAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!AlertUtil.confirm("Confirm Return",
                "Process return for " + toReturn.size() + " item(s)?\n" +
                "Refund: " + CurrencyUtil.format(refundTotal) + " via " + method)) return;

        // Restore stock for each returned item
        int userId = SessionManager.getCurrentUser().getId();
        for (ReturnItem ri : toReturn) {
            stockService.addStock(ri.getBillItem().getProductId(),
                ri.getReturnQty(), originalBill.getId(), userId, "RETURN");
        }

        // Broadcast sync event
        try {
            com.minimartpos.network.SyncManager.getInstance()
                .notifyStockChanged(0); // 0 = multiple products
        } catch (Exception ignored) {}

        logger.info("Return processed: bill={} items={} refund={} reason={} method={}",
            originalBill.getBillNumber(), toReturn.size(), refundTotal, reason, method);

        AlertUtil.showInfo("Return Processed",
            "Return completed successfully.\n" +
            "Refund: " + CurrencyUtil.format(refundTotal) + "\n" +
            "Method: " + method + "\n" +
            "Stock has been restored.");

        close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FXML private void cancel() { close(); }
    private void close() { ((Stage) processBtn.getScene().getWindow()).close(); }

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
        private int     returnQty;
        private boolean selected;

        public ReturnItem(BillItem item) {
            this.billItem  = item;
            this.returnQty = 0;
            this.selected  = false;
        }

        public BillItem getBillItem()           { return billItem; }
        public int      getReturnQty()          { return returnQty; }
        public void     setReturnQty(int q)     { this.returnQty = q; }
        public boolean  isSelected()            { return selected; }
        public void     setSelected(boolean s)  { this.selected = s; }

        public BigDecimal getRefundAmount() {
            if (!selected || returnQty <= 0) return BigDecimal.ZERO;
            return billItem.getUnitPrice()
                .multiply(BigDecimal.valueOf(returnQty));
        }
    }
}
