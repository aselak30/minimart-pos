package com.minimartpos.controller.cashier;

import com.minimartpos.model.Shift;
import com.minimartpos.repository.ShiftRepository;
import com.minimartpos.security.SessionManager;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ResourceBundle;


/**
 * End-of-shift dialog controller.
 * Shows shift summary, collects closing cash, computes variance, closes shift.
 */
public class ShiftController implements Initializable {

    @FXML private Label     cashierLabel;
    @FXML private Label     startTimeLabel;
    @FXML private Label     durationLabel;
    @FXML private Label     billsLabel;
    @FXML private Label     salesLabel;
    @FXML private Label     cashSalesLabel;
    @FXML private Label     cardSalesLabel;
    @FXML private Label     openingCashLabel;
    @FXML private Label     expectedCashLabel;
    @FXML private TextField closingCashField;
    @FXML private Label     varianceLabel;
    @FXML private TextArea  notesField;
    @FXML private Label     errorLabel;

    private final ShiftRepository shiftRepo = new ShiftRepository();
    private Shift   activeShift;
    private Runnable onShiftClosed;      // callback for POSTerminalController to handle logout

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadShiftData();
    }

    /** Called before showing — sets the callback to invoke after shift close. */
    public void setOnShiftClosed(Runnable callback) {
        this.onShiftClosed = callback;
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadShiftData() {
        int cashierId = SessionManager.getCurrentUser().getId();
        Optional<Shift> opt = shiftRepo.findOpenShiftForCashier(cashierId);

        cashierLabel.setText(SessionManager.getCurrentUser().getFullName());

        if (opt.isPresent()) {
            activeShift = opt.get();
            LocalDateTime start = activeShift.getStartTime();
            startTimeLabel.setText(start != null ? DateUtil.formatDateTime(start) : "—");

            if (start != null) {
                Duration dur = Duration.between(start, LocalDateTime.now());
                durationLabel.setText(String.format("%dh %02dm",
                    dur.toHours(), dur.toMinutesPart()));
            }
            openingCashLabel.setText(CurrencyUtil.format(activeShift.getOpeningCash()));
        } else {
            startTimeLabel.setText("No active shift found");
        }

        // Load today's sales stats for this cashier from DB
        loadCashierStats(cashierId);
    }

    private void loadCashierStats(int cashierId) {
        // Inline query for today's stats
        try (var conn = com.minimartpos.config.DatabaseConfig.getConnection();
             var ps = conn.prepareStatement(
                 "SELECT COUNT(*) AS bills, " +
                 "  COALESCE(SUM(total_amount),0) AS sales, " +
                 "  COALESCE(SUM(CASE WHEN payment_type='CASH' THEN total_amount ELSE 0 END),0) AS cash_sales, " +
                 "  COALESCE(SUM(CASE WHEN payment_type='CARD' THEN total_amount ELSE 0 END),0) AS card_sales " +
                 "FROM bills WHERE cashier_id=? AND status='FINALIZED' AND DATE(finalized_at)=CURDATE()")) {
            ps.setInt(1, cashierId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal sales    = rs.getBigDecimal("sales");
                    BigDecimal cashSales = rs.getBigDecimal("cash_sales");
                    BigDecimal opening  = activeShift != null
                        ? activeShift.getOpeningCash() : BigDecimal.ZERO;

                    billsLabel.setText(String.valueOf(rs.getInt("bills")));
                    salesLabel.setText(CurrencyUtil.format(sales));
                    cashSalesLabel.setText(CurrencyUtil.format(cashSales));
                    cardSalesLabel.setText(CurrencyUtil.format(rs.getBigDecimal("card_sales")));

                    BigDecimal expected = opening.add(cashSales);
                    expectedCashLabel.setText(CurrencyUtil.format(expected));
                }
            }
        } catch (Exception e) {
            billsLabel.setText("—"); salesLabel.setText("—");
        }
    }

    // ── Variance ──────────────────────────────────────────────────────────────

    @FXML
    private void onClosingCashChanged() {
        BigDecimal closing  = CurrencyUtil.parse(closingCashField.getText());
        BigDecimal expected = CurrencyUtil.parse(expectedCashLabel.getText().replace("Rs.", "").trim());
        BigDecimal variance = closing.subtract(expected);

        varianceLabel.setText(CurrencyUtil.format(variance));
        varianceLabel.setStyle("-fx-font-weight:bold; -fx-font-size:15px; -fx-text-fill:" +
            (variance.abs().compareTo(BigDecimal.valueOf(100)) > 0
                ? "-pos-danger;" : "-pos-success;"));
    }

    // ── Close Shift ───────────────────────────────────────────────────────────

    @FXML
    private void closeShift() {
        BigDecimal closing = CurrencyUtil.parse(closingCashField.getText());
        if (closing.compareTo(BigDecimal.ZERO) < 0) {
            showError("Closing cash cannot be negative.");
            return;
        }
        if (!AlertUtil.confirm("Close Shift",
                "Are you sure you want to close this shift? This cannot be undone.")) return;

        if (activeShift != null) {
            shiftRepo.closeShift(activeShift.getId(), closing);
        }

        Stage stage = (Stage) closingCashField.getScene().getWindow();
        stage.close();

        if (onShiftClosed != null) onShiftClosed.run();
    }

    @FXML private void cancel() {
        ((Stage) closingCashField.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
