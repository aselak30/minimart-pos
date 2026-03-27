package com.minimartpos.controller.cashier;

import com.minimartpos.model.Bill;
import com.minimartpos.util.CurrencyUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;

/**
 * Controller for the Payment dialog.
 * Handles Cash / Card / Mobile / Credit payment flows.
 */
public class PaymentController implements Initializable {

    @FXML private Label     titleLabel;
    @FXML private Label     billNumLabel;
    @FXML private Label     itemsLabel;
    @FXML private Label     totalAmountLabel;
    @FXML private TextField tenderedField;
    @FXML private Label     tenderedDisplayLabel;
    @FXML private Label     changeLabel;
    @FXML private Label     errorLabel;
    @FXML private Button    confirmBtn;
    @FXML private HBox      quickAmountRow1;
    @FXML private HBox      quickAmountRow2;
    @FXML private VBox      quickAmountsPane;

    private Bill                                 bill;
    private Bill.PayType                         payType;
    private BiConsumer<BigDecimal, Bill.PayType> onConfirm;
    private BigDecimal                           totalAmount;

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    /**
     * Called by POSTerminalController before showing the dialog.
     */
    public void setup(Bill bill, Bill.PayType payType,
                      BiConsumer<BigDecimal, Bill.PayType> onConfirm) {
        this.bill        = bill;
        this.payType     = payType;
        this.onConfirm   = onConfirm;
        this.totalAmount = bill.getTotalAmount();

        String typeLabel = switch (payType) {
            case CASH         -> "💵  Cash Payment";
            case CARD         -> "💳  Card Payment";
            case MOBILE_MONEY -> "📱  Mobile Payment";
            case CREDIT       -> "📋  Credit Sale";
            default           -> "Payment";
        };
        titleLabel.setText(typeLabel);
        billNumLabel.setText(bill.getBillNumber());
        itemsLabel.setText(bill.getItemCount() + " item(s)");
        totalAmountLabel.setText(CurrencyUtil.format(totalAmount));
        confirmBtn.setText(payType == Bill.PayType.CREDIT ? "✔ Create Credit Bill" : "✔ Confirm Payment");

        if (payType != Bill.PayType.CASH) {
            tenderedField.setText(CurrencyUtil.formatPlain(totalAmount));
            quickAmountsPane.setVisible(false);
            quickAmountsPane.setManaged(false);
            updateChange();
        } else {
            buildQuickAmountButtons();
            tenderedField.requestFocus();
        }
    }

    // ── Quick Amount Buttons ──────────────────────────────────────────────────

    private void buildQuickAmountButtons() {
        double total = totalAmount.doubleValue();
        double[] amounts = generateQuickAmounts(total);
        quickAmountRow1.getChildren().clear();
        quickAmountRow2.getChildren().clear();
        for (int i = 0; i < amounts.length; i++) {
            double amt = amounts[i];
            Button btn = new Button("Rs. " + formatQuick(amt));
            btn.setStyle("-fx-background-color:white; -fx-border-color:-pos-primary; " +
                         "-fx-border-width:1.5; -fx-border-radius:6; -fx-background-radius:6; " +
                         "-fx-cursor:hand; -fx-font-size:12px;");
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            final double a = amt;
            btn.setOnAction(e -> { tenderedField.setText(String.valueOf(a)); updateChange(); });
            if (i < 4) quickAmountRow1.getChildren().add(btn);
            else        quickAmountRow2.getChildren().add(btn);
        }
    }

    private double[] generateQuickAmounts(double total) {
        return java.util.Arrays.stream(new double[]{
                ceilTo(total, 10), ceilTo(total, 50), ceilTo(total, 100),
                ceilTo(total, 500), ceilTo(total, 1000), ceilTo(total, 5000)})
            .filter(a -> a >= total).distinct().limit(6).toArray();
    }

    private double ceilTo(double v, double m) { return Math.ceil(v / m) * m; }

    private String formatQuick(double a) {
        return (a == (long) a) ? String.valueOf((long) a) : String.format("%.2f", a);
    }

    // ── Change Calculation ────────────────────────────────────────────────────

    @FXML
    private void onTenderedChanged() { updateChange(); }

    private void updateChange() {
        clearError();
        BigDecimal tendered = CurrencyUtil.parse(tenderedField.getText());
        tenderedDisplayLabel.setText(CurrencyUtil.formatPlain(tendered));

        if (tendered.compareTo(BigDecimal.ZERO) <= 0) {
            changeLabel.setText("—");
            changeLabel.setStyle("-fx-text-fill:-pos-text-secondary; -fx-font-size:22px;");
            confirmBtn.setDisable(payType != Bill.PayType.CREDIT);
            return;
        }

        if (payType == Bill.PayType.CREDIT) {
            changeLabel.setText("Credit sale");
            changeLabel.setStyle("-fx-text-fill:-pos-warning; -fx-font-size:18px; -fx-font-weight:bold;");
            confirmBtn.setDisable(false);
        } else {
            BigDecimal change = tendered.subtract(totalAmount);
            if (change.compareTo(BigDecimal.ZERO) < 0) {
                changeLabel.setText(CurrencyUtil.format(change.abs()) + " short");
                changeLabel.setStyle("-fx-text-fill:-pos-danger; -fx-font-size:18px; -fx-font-weight:bold;");
                confirmBtn.setDisable(true);
            } else {
                changeLabel.setText(CurrencyUtil.format(change));
                changeLabel.setStyle("-fx-text-fill:-pos-success; -fx-font-size:22px; -fx-font-weight:bold;");
                confirmBtn.setDisable(false);
            }
        }
    }

    // ── Confirm / Cancel ──────────────────────────────────────────────────────

    @FXML
    private void confirmPayment() {
        BigDecimal tendered;
        if (payType == Bill.PayType.CREDIT) {
            tendered = totalAmount;
        } else {
            tendered = CurrencyUtil.parse(tenderedField.getText());
            if (tendered.compareTo(totalAmount) < 0) {
                showError("Tendered amount is less than total.");
                return;
            }
        }
        closeStage();
        onConfirm.accept(tendered, payType);
    }

    @FXML
    private void cancel() { closeStage(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void closeStage() {
        ((Stage) confirmBtn.getScene().getWindow()).close();
    }
    private void showError(String msg) {
        errorLabel.setText(msg); errorLabel.setVisible(true); errorLabel.setManaged(true);
    }
    private void clearError() {
        errorLabel.setVisible(false); errorLabel.setManaged(false);
    }
}
