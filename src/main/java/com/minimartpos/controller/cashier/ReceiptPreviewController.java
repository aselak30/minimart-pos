package com.minimartpos.controller.cashier;

import com.minimartpos.hardware.PrinterManager;
import com.minimartpos.model.Bill;
import com.minimartpos.service.SettingsService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.PrintUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Receipt preview dialog shown after every completed bill.
 *
 * Shows the formatted receipt text and offers:
 * - 🖨 Print — sends to printer (ESC/POS or fallback)
 * - ⏭ Skip — closes without printing
 *
 * Also used for Reprint from the POS toolbar.
 */
public class ReceiptPreviewController implements Initializable {

    private static final Logger logger = LogManager.getLogger(ReceiptPreviewController.class);

    @FXML
    private Label billNumberLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label printerStatusLabel;
    @FXML
    private TextArea receiptPreview;
    @FXML
    private Button printBtn;
    @FXML
    private Button skipBtn;
    @FXML
    private Label printResultLabel;
    @FXML
    private VBox root;

    private Bill bill;
    private String receiptText;
    private final SettingsService settingsService = new SettingsService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Start printer status check in background to avoid freezing the UI
        printerStatusLabel.setText("⏳ Checking printer status...");
        printerStatusLabel.setStyle("-fx-text-fill:-pos-text-secondary; -fx-font-size:11px;");

        new Thread(() -> {
            PrinterManager pm = new PrinterManager();
            String configured = settingsService.get("receipt_printer", "");
            boolean hasPrinter = pm.testPrinter(configured);
            
            Platform.runLater(() -> {
                if (hasPrinter && printerStatusLabel != null) {
                    String name = (configured == null || configured.isBlank()) ? "Default printer" : configured;
                    printerStatusLabel.setText("🖨 Printer ready: " + name);
                    printerStatusLabel.setStyle("-fx-text-fill:-pos-success; -fx-font-size:11px;");
                } else if (printerStatusLabel != null) {
                    printerStatusLabel.setText("⚠ No printer configured — go to Settings → Receipt Printer");
                    printerStatusLabel.setStyle("-fx-text-fill:-pos-warning; -fx-font-size:11px;");
                    if (printBtn != null) printBtn.setDisable(false);
                }
            });
        }, "printer-check-thread").start();
    }

    /**
     * Called by POSTerminalController before showing the dialog.
     */
    public void setBill(Bill bill) {
        this.bill = bill;

        String company = settingsService.get("company_name", "MiniMart");
        String address = settingsService.get("company_address", "");
        String phone = settingsService.get("company_phone", "");
        String footer = settingsService.get("receipt_footer", "Thank you for your visit!");
        String template = settingsService.get("receipt_template", "");

        this.receiptText = PrintUtil.buildReceipt(bill, company, address, phone, footer, template);

        // 2. Prep preview UI with settings styles
        String fontFamily = settingsService.get("receipt_font", "Courier New");
        boolean forceBold = "1".equals(settingsService.get("receipt_force_bold", "0"));

        // Strip [B] markers for the on-screen TextArea (which doesn't support per-line bolding)
        String previewText = receiptText.replace("[B]", "");

        billNumberLabel.setText("Bill #" + bill.getBillNumber());
        totalLabel.setText("Total: " + com.minimartpos.util.CurrencyUtil.format(bill.getTotalAmount()));
        
        receiptPreview.setText(previewText);
        receiptPreview.setStyle("-fx-font-family: '" + fontFamily + "'; " +
                               "-fx-font-size: 11px; " +
                               "-fx-font-weight: " + (forceBold ? "bold" : "normal") + ";");
        receiptPreview.positionCaret(0);
    }

    @FXML
    private void onPrint() {
        printBtn.setDisable(true);
        printBtn.setText("Printing…");
        printResultLabel.setText("");

        new Thread(() -> {
            boolean ok = false;
            try {
                PrinterManager pm = new PrinterManager();
                ok = pm.printReceipt(bill);
            } catch (Exception e) {
                logger.error("Receipt print error: {}", e.getMessage(), e);
            }
            final boolean success = ok;
            Platform.runLater(() -> {
                if (success) {
                    printResultLabel.setText("✔ Printed successfully");
                    printResultLabel.setStyle("-fx-text-fill:-pos-success; -fx-font-weight:bold;");
                    printBtn.setText("🖨 Print Again");
                    printBtn.setDisable(false);
                    // Auto-close after 1.5 s
                    new javafx.animation.PauseTransition(
                            javafx.util.Duration.seconds(1.5))
                            .play();
                    closeAfterDelay(1500);
                } else {
                    printResultLabel.setText("✖ Print failed — check printer connection");
                    printResultLabel.setStyle("-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
                    printBtn.setText("🖨 Retry Print");
                    printBtn.setDisable(false);
                }
            });
        }, "receipt-print").start();
    }

    @FXML
    private void onSkip() {
        close();
    }

    private void closeAfterDelay(long ms) {
        new Thread(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ignored) {
            }
            Platform.runLater(this::close);
        }).start();
    }

    private void close() {
        Stage stage = (Stage) skipBtn.getScene().getWindow();
        stage.close();
    }
}
