package com.minimartpos.controller.admin;

import com.minimartpos.config.AppConfig;
import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.AutoBackupScheduler;
import com.minimartpos.service.BackupService;
import com.minimartpos.service.SettingsService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.SceneManager;
import com.minimartpos.util.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * System Settings screen controller.
 * Loads all settings from DB and saves them back in bulk on Save.
 */
public class SettingsController implements Initializable {

    private static final Logger logger = LogManager.getLogger(SettingsController.class);

    @FXML
    private Label sidebarUserLabel;
    @FXML
    private Label sidebarCompanyLabel;
    @FXML
    private TextField companyName;
    @FXML
    private TextField companyPhone;
    @FXML
    private TextField companyEmail;
    @FXML
    private TextField currencySymbol;
    @FXML
    private TextArea companyAddress;
    @FXML
    private TextField receiptFooter;
    @FXML
    private CheckBox taxInclusiveCheck;
    @FXML
    private TextField receiptPrinter;
    @FXML
    private TextField companyLogoField;
    @FXML
    private TextField receiptLogoField;
    @FXML
    private TextArea receiptTemplateArea;
    @FXML
    private ComboBox<String> receiptFontChoice;
    @FXML
    private CheckBox receiptForceBold;
    @FXML
    private TextField expiryWarningDays;
    @FXML
    private TextField sessionTimeout;
    @FXML
    private CheckBox lowStockAlertCheck;
    @FXML
    private Label dbHostLabel;
    @FXML
    private Label dbNameLabel;
    @FXML
    private Label dbUserLabel;
    @FXML
    private Label dbStatusLabel;
    @FXML
    private Label appVersionLabel;
    @FXML
    private Label javaVersionLabel;
    @FXML
    private Label feedbackLabel;
    // Network / sync
    @FXML
    private Label localMachineLabel;
    @FXML
    private Label localIpLabel;
    @FXML
    private Label syncStatusLabel;
    @FXML
    private Label onlineMachinesLabel;
    @FXML
    private Label offlineQueueLabel;
    // Auto backup
    @FXML
    private CheckBox checkAutoBackup;
    @FXML
    private TextField fieldBackupTime;
    @FXML
    private TextField fieldBackupKeepDays;
    @FXML
    private CheckBox checkCashAlert;
    @FXML
    private Label lastBackupLabel;
    @FXML
    private Label lastBackupStatusLabel;
    @FXML
    private Label nextBackupLabel;
    // Theme + MySQL bin path (new)
    @FXML
    private HBox themePickerBox; // may be null if FXML not yet updated — handled gracefully
    @FXML
    private TextField mysqlBinPathField;
    @FXML
    private Label backupStatusLabel; // diagnostic label in backup section

    private final SettingsService settingsService = new SettingsService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        sidebarCompanyLabel.setText("🛒 " + settingsService.company());
        if (receiptFontChoice != null) {
            receiptFontChoice.setItems(javafx.collections.FXCollections.observableArrayList(
                "Monospaced", "Consolas", "Courier New", "SansSerif", "Arial"
            ));
        }
        loadSettings();
        populateSysInfo();
        buildThemePicker();
        logger.info("Settings screen initialized");
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    private void loadSettings() {
        Map<String, String> s = settingsService.getAll();
        companyName.setText(s.getOrDefault("company_name", "MiniMart"));
        companyPhone.setText(s.getOrDefault("company_phone", ""));
        companyEmail.setText(s.getOrDefault("company_email", ""));
        currencySymbol.setText(s.getOrDefault("currency_symbol", "Rs."));
        companyAddress.setText(s.getOrDefault("company_address", ""));
        receiptFooter.setText(s.getOrDefault("receipt_footer", "Thank you for your visit!"));
        taxInclusiveCheck.setSelected("1".equals(s.getOrDefault("tax_inclusive", "0")));
        receiptPrinter.setText(s.getOrDefault("receipt_printer", ""));
        if (companyLogoField != null)
            companyLogoField.setText(s.getOrDefault("company_logo", "images/logo.png"));
        if (receiptLogoField != null)
            receiptLogoField.setText(s.getOrDefault("receipt_logo", "images/logo.png"));
        if (receiptTemplateArea != null) {
            String template = s.getOrDefault("receipt_template", com.minimartpos.util.PrintUtil.DEFAULT_TEMPLATE);
            if (template.isEmpty()) template = com.minimartpos.util.PrintUtil.DEFAULT_TEMPLATE;
            receiptTemplateArea.setText(template);
        }
        if (receiptFontChoice != null)
            receiptFontChoice.setValue(s.getOrDefault("receipt_font", "Monospaced"));
        if (receiptForceBold != null)
            receiptForceBold.setSelected("1".equals(s.getOrDefault("receipt_force_bold", "0")));
        expiryWarningDays.setText(s.getOrDefault("expiry_warning_days", "30"));
        sessionTimeout.setText(s.getOrDefault("session_timeout", "30"));
        lowStockAlertCheck.setSelected("1".equals(s.getOrDefault("low_stock_alert", "1")));

        // Auto-backup
        checkAutoBackup.setSelected(!"0".equals(s.getOrDefault("auto_backup_enabled", "1")));
        fieldBackupTime.setText(s.getOrDefault("auto_backup_time", "02:00"));
        fieldBackupKeepDays.setText(s.getOrDefault("auto_backup_keep_days", "30"));
        checkCashAlert.setSelected(!"0".equals(s.getOrDefault("cash_alert_enabled", "1")));

        // Last backup status
        AutoBackupScheduler scheduler = AutoBackupScheduler.getInstance();
        if (scheduler.getLastBackupTime() != null) {
            lastBackupLabel.setText(scheduler.getLastBackupTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            lastBackupStatusLabel.setText(scheduler.wasLastBackupSuccessful()
                    ? "✔ Success"
                    : "✖ Failed");
            lastBackupStatusLabel.setStyle(scheduler.wasLastBackupSuccessful()
                    ? "-fx-text-fill:-pos-success; -fx-font-weight:bold;"
                    : "-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
        } else {
            lastBackupLabel.setText("Never");
        }
        nextBackupLabel.setText(scheduler.getNextBackupTime());

        // MySQL bin path for backup
        if (mysqlBinPathField != null)
            mysqlBinPathField.setText(s.getOrDefault("mysql_bin_path", ""));
    }

    @FXML
    private void saveSettings() {
        // Basic validation
        if (companyName.getText().isBlank()) {
            showFeedback("Company name cannot be empty.", false);
            return;
        }
        try {
            int exp = Integer.parseInt(expiryWarningDays.getText().trim());
            int ses = Integer.parseInt(sessionTimeout.getText().trim());
            if (exp < 0 || ses < 1)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showFeedback("Expiry warning days and session timeout must be positive numbers.", false);
            return;
        }

        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("company_name", companyName.getText().trim());
        settings.put("company_phone", companyPhone.getText().trim());
        settings.put("company_email", companyEmail.getText().trim());
        settings.put("currency_symbol", currencySymbol.getText().trim());
        settings.put("company_address", companyAddress.getText().trim());
        settings.put("receipt_footer", receiptFooter.getText().trim());
        settings.put("tax_inclusive", taxInclusiveCheck.isSelected() ? "1" : "0");
        settings.put("receipt_printer", receiptPrinter.getText().trim());
        if (companyLogoField != null)
            settings.put("company_logo", companyLogoField.getText().trim());
        if (receiptLogoField != null)
            settings.put("receipt_logo", receiptLogoField.getText().trim());
        if (receiptFontChoice != null && receiptFontChoice.getValue() != null)
            settings.put("receipt_font", receiptFontChoice.getValue());
        if (receiptForceBold != null)
            settings.put("receipt_force_bold", receiptForceBold.isSelected() ? "1" : "0");
        if (receiptTemplateArea != null)
            settings.put("receipt_template", receiptTemplateArea.getText());
        settings.put("expiry_warning_days", expiryWarningDays.getText().trim());
        settings.put("session_timeout", sessionTimeout.getText().trim());
        settings.put("low_stock_alert", lowStockAlertCheck.isSelected() ? "1" : "0");
        settings.put("auto_backup_enabled", checkAutoBackup.isSelected() ? "1" : "0");
        settings.put("auto_backup_time", fieldBackupTime.getText().trim());
        settings.put("auto_backup_keep_days", fieldBackupKeepDays.getText().trim());
        settings.put("cash_alert_enabled", checkCashAlert.isSelected() ? "1" : "0");

        // MySQL bin path
        if (mysqlBinPathField != null && !mysqlBinPathField.getText().isBlank())
            settings.put("mysql_bin_path", mysqlBinPathField.getText().trim());

        boolean success = settingsService.saveAll(settings);
        if (success) {
            showFeedback("✔ Settings saved successfully.", true);
            loadSettings(); // Refresh UI to match DB
            logger.info("Settings saved by {}", SessionManager.getCurrentUser().getUsername());
        } else {
            showFeedback("✖ Failed to save settings. Check database logs.", false);
        }
    }

    // ── Database actions ──────────────────────────────────────────────────────

    @FXML
    private void browseCompanyLogo() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Select Application Logo");
        fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp")
        );
        java.io.File file = fc.showOpenDialog(companyName.getScene().getWindow());
        if (file != null) {
            companyLogoField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void clearCompanyLogo() {
        companyLogoField.clear();
    }

    @FXML
    private void browseLogo() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Select Bill Logo");
        fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp")
        );
        java.io.File file = fc.showOpenDialog(companyName.getScene().getWindow());
        if (file != null) {
            receiptLogoField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void clearLogo() {
        receiptLogoField.clear();
    }

    @FXML
    private void resetReceiptTemplate() {
        if (receiptTemplateArea != null) {
            boolean confirm = AlertUtil.confirm("Reset Template", "Are you sure you want to reset the receipt layout to the standard default format?\n\nAny unsaved custom formatting will be lost.");
            if (confirm) {
                receiptTemplateArea.setText(com.minimartpos.util.PrintUtil.DEFAULT_TEMPLATE);
            }
        }
    }

    @FXML
    private void testConnection() {
        boolean ok = DatabaseConfig.isConnected();
        AlertUtil.showInfo("Connection Test",
                ok ? "✔ Database connection is active." : "✖ Database is not connected.");
    }

    @FXML
    private void changeDbConnection() {
        com.minimartpos.util.SceneManager.navigateTo("shared/DatabaseSetup.fxml");
    }

    @FXML
    private void backupNow() {
        showFeedback("Starting backup…", true);
        new Thread(() -> {
            AutoBackupScheduler.BackupResult result = AutoBackupScheduler.getInstance().runNow();
            javafx.application.Platform.runLater(() -> {
                showFeedback(result.getMessage(), result.isSuccess());
                // Refresh last backup labels
                AutoBackupScheduler scheduler = AutoBackupScheduler.getInstance();
                if (scheduler.getLastBackupTime() != null) {
                    lastBackupLabel.setText(scheduler.getLastBackupTime()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    lastBackupStatusLabel.setText(result.isSuccess() ? "✔ Success" : "✖ Failed");
                    lastBackupStatusLabel.setStyle(result.isSuccess()
                            ? "-fx-text-fill:-pos-success; -fx-font-weight:bold;"
                            : "-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
                }
                if (result.isSuccess()) {
                    AlertUtil.showInfo("Backup Complete",
                            "Backup saved successfully.\n" +
                                    (result.getFile() != null ? result.getFile().getAbsolutePath() : ""));
                }
            });
        }, "backup-now-thread").start();
    }

    @FXML
    private void backupDatabase() {
        javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
        dc.setTitle("Choose Backup Folder");
        java.io.File dir = dc.showDialog(companyName.getScene().getWindow());
        if (dir == null)
            return;

        settingsService.set("backup_path", dir.getAbsolutePath());
        backupNow();
    }

    @FXML
    private void testMysqldump() {
        // Save path first if provided
        if (mysqlBinPathField != null && !mysqlBinPathField.getText().isBlank()) {
            settingsService.set("mysql_bin_path", mysqlBinPathField.getText().trim());
        }
        BackupService bs = new BackupService();
        String diag = bs.getMysqldumpDiagnostic();
        boolean found = diag.startsWith("mysqldump found");
        if (backupStatusLabel != null) {
            backupStatusLabel.setText(found ? "✔ " + diag : "✖ " + diag);
            backupStatusLabel.setStyle(found
                    ? "-fx-text-fill:-pos-success; -fx-font-weight:bold; -fx-font-size:11px;"
                    : "-fx-text-fill:-pos-danger; -fx-font-weight:bold; -fx-font-size:11px;");
        }
        AlertUtil.showInfo(found ? "mysqldump Found" : "mysqldump Not Found", diag);
    }

    // ── Theme Picker ─────────────────────────────────────────────────────────────

    /**
     * Programmatically builds colour-swatch theme buttons and injects them into
     * themePickerBox (an HBox in Settings.fxml). If themePickerBox is null
     * (older FXML without the node) the method exits silently so the rest of the
     * screen still works.
     */
    private void buildThemePicker() {
        if (themePickerBox == null)
            return;
        themePickerBox.getChildren().clear();
        String currentTheme = ThemeManager.getUserTheme();

        for (Map.Entry<String, ThemeManager.ThemeDefinition> entry : ThemeManager.THEMES.entrySet()) {
            String key = entry.getKey();
            ThemeManager.ThemeDefinition def = entry.getValue();

            // Colour swatch circle
            Circle swatch = new Circle(14);
            try {
                swatch.setFill(Color.web(def.primaryColor()));
            } catch (Exception e) {
                swatch.setFill(Color.GRAY);
            }
            swatch.setStyle("-fx-stroke: -pos-border; -fx-stroke-width: 1.5;");

            // Highlight active
            if (key.equals(currentTheme)) {
                swatch.setStyle("-fx-stroke: -pos-primary; -fx-stroke-width: 3;");
            }

            Button btn = new Button(def.displayName());
            btn.setGraphic(swatch);
            btn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
            btn.setStyle(
                    "-fx-background-radius:20; -fx-padding:5 12; -fx-cursor:hand; -fx-font-size:12px;" +
                            (key.equals(currentTheme)
                                    ? "-fx-border-color:-pos-primary; -fx-border-width:2; -fx-border-radius:20; -fx-font-weight:bold;"
                                    : "-fx-border-color:-pos-border; -fx-border-width:1; -fx-border-radius:20;"));
            btn.setOnAction(e -> {
                ThemeManager.setUserTheme(key);
                buildThemePicker(); // re-render to show active state
                showFeedback("✔ Theme changed to " + def.displayName(), true);
            });
            themePickerBox.getChildren().add(btn);
        }
    }

    // ── Sys Info ──────────────────────────────────────────────────────────────

    private void populateSysInfo() {
        dbHostLabel.setText(DatabaseConfig.isConnected() ? DatabaseConfig.getDbHost() : "—");
        dbNameLabel.setText(DatabaseConfig.isConnected() ? DatabaseConfig.getDbName() : "—");
        dbUserLabel.setText(DatabaseConfig.isConnected() ? DatabaseConfig.getDbUser() : "—");
        dbStatusLabel.setText(DatabaseConfig.isConnected() ? "● Connected" : "✖ Disconnected");
        dbStatusLabel.setStyle(DatabaseConfig.isConnected()
                ? "-fx-text-fill:-pos-success; -fx-font-weight:bold;"
                : "-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
        appVersionLabel.setText("v" + AppConfig.APP_VERSION);
        javaVersionLabel.setText(System.getProperty("java.version"));
        refreshNetworkStatus();
    }

    @FXML
    private void refreshNetworkStatus() {
        try {
            com.minimartpos.network.SyncManager sm = com.minimartpos.network.SyncManager.getInstance();
            com.minimartpos.network.NetworkMonitor nm = sm.getNetworkMonitor();

            localMachineLabel.setText(nm.getLocalMachineCode() != null
                    ? nm.getLocalMachineCode()
                    : "Detecting…");
            localIpLabel.setText(nm.getLocalIpAddress() != null
                    ? nm.getLocalIpAddress()
                    : "—");

            boolean syncRunning = sm.isRunning();
            syncStatusLabel.setText(syncRunning ? "● Active" : "○ Inactive");
            syncStatusLabel.setStyle(syncRunning
                    ? "-fx-text-fill:-pos-success; -fx-font-weight:bold;"
                    : "-fx-text-fill:-pos-warning; -fx-font-weight:bold;");

            int online = nm.getOnlineCount();
            onlineMachinesLabel.setText(online + " machine" + (online == 1 ? "" : "s") + " online");

            // Offline queue status
            int pending = com.minimartpos.network.OfflineSync.getInstance().pendingCount();
            offlineQueueLabel.setText(pending == 0 ? "0 pending (all synced)"
                    : pending + " bill" + (pending == 1 ? "" : "s") + " pending sync");
            offlineQueueLabel.setStyle(pending > 0
                    ? "-fx-text-fill:-pos-warning; -fx-font-weight:bold;"
                    : "-fx-text-fill:-pos-success;");
        } catch (Exception e) {
            localMachineLabel.setText("—");
            syncStatusLabel.setText("○ Not started");
            syncStatusLabel.setStyle("-fx-text-fill:-pos-warning;");
            onlineMachinesLabel.setText("—");
        }
    }
    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showFeedback(String msg, boolean success) {
        feedbackLabel.setText(msg);
        feedbackLabel.setStyle(success
                ? "-fx-text-fill:-pos-success; -fx-font-weight:bold; -fx-font-size:13px;"
                : "-fx-text-fill:-pos-danger; -fx-font-weight:bold; -fx-font-size:13px;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    @FXML
    private void navigateToDashboard() {
        com.minimartpos.util.SceneManager.navigateTo("admin/AdminDashboard.fxml");
    }

    @FXML
    private void navigateToPOS() {
        com.minimartpos.util.SceneManager.navigateTo("cashier/POSTerminal.fxml");
    }

    @FXML
    private void navigateToUsers() {
        com.minimartpos.util.SceneManager.navigateTo("admin/UserManagement.fxml");
    }

    @FXML
    private void navigateToProducts() {
        com.minimartpos.util.SceneManager.navigateTo("admin/ProductManagement.fxml");
    }

    @FXML
    private void navigateToCustomers() {
        com.minimartpos.util.SceneManager.navigateTo("admin/CustomerManagement.fxml");
    }

    @FXML
    private void navigateToSuppliers() {
        com.minimartpos.util.SceneManager.navigateTo("admin/SupplierManagement.fxml");
    }

    @FXML
    private void navigateToCashierMonitor() {
        com.minimartpos.util.SceneManager.navigateTo("admin/CashierMonitor.fxml");
    }

    @FXML
    private void navigateToBills() {
        com.minimartpos.util.SceneManager.navigateTo("admin/BillHistory.fxml");
    }

    @FXML
    private void navigateToStock() {
        com.minimartpos.util.SceneManager.navigateTo("admin/StockAdjustment.fxml");
    }

    @FXML
    private void navigateToReports() {
        com.minimartpos.util.SceneManager.navigateTo("admin/Reports.fxml");
    }

    @FXML
    private void navigateToAudit() {
        com.minimartpos.util.SceneManager.navigateTo("admin/AuditLog.fxml");
    }

    @FXML
    private void navigateToSettings() {
        com.minimartpos.util.SceneManager.navigateTo("admin/Settings.fxml");
    }

}
