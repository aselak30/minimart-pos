package com.minimartpos.controller.admin;

import com.minimartpos.config.AppConfig;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.BackupScheduler;
import com.minimartpos.service.BackupService;
import com.minimartpos.service.SettingsService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.SceneManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ResourceBundle;

public class BackupManagerController implements Initializable {

    private static final Logger logger = LogManager.getLogger(BackupManagerController.class);

    @FXML private Label     sidebarUserLabel;
    @FXML private Label     sidebarCompanyLabel;
    @FXML private Label     schedulerStatusLabel;
    @FXML private CheckBox  autoBackupCheck;
    @FXML private TextField backupTimeField;
    @FXML private TextField backupPathField;
    @FXML private TextField keepDaysField;
    @FXML private Label     nextBackupLabel;
    @FXML private Label     lastBackupLabel;
    @FXML private Label     backupStatusLabel;
    @FXML private ProgressBar backupProgress;
    @FXML private Label     backupCountLabel;
    @FXML private Label     restoreStatusLabel;

    @FXML private TableView<File>            historyTable;
    @FXML private TableColumn<File, String>  colBackupName;
    @FXML private TableColumn<File, String>  colBackupDate;
    @FXML private TableColumn<File, String>  colBackupSize;
    @FXML private TableColumn<File, String>  colBackupType;
    @FXML private TableColumn<File, String>  colBackupActions;

    private final BackupService   backupService   = new BackupService();
    private final BackupScheduler scheduler       = BackupScheduler.getInstance();
    private final SettingsService settingsService = new SettingsService();

    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        sidebarCompanyLabel.setText("🛒 " + settingsService.company());
        setupColumns();
        loadSettings();
        refreshHistory();
        updateSchedulerStatus();
        // Show mysqldump availability at startup
        String diag = backupService.getMysqldumpDiagnostic();
        boolean found = diag.startsWith("mysqldump found");
        setBackupStatus(found ? "✔  " + diag : "⚠  " + diag, found ? true : false);

        // Wire backup complete callback to update UI
        scheduler.setOnBackupComplete(file -> {
            refreshHistory();
            setBackupStatus("✔  Last backup: " + file.getName(), true);
            updateLastBackupLabel();
        });
        scheduler.setOnBackupFailed(msg ->
            setBackupStatus("✖  " + msg, false));
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private void loadSettings() {
        autoBackupCheck.setSelected(scheduler.isAutoEnabled());
        backupTimeField.setText(scheduler.getConfiguredTime().toString());
        backupPathField.setText(scheduler.getBackupDir());
        keepDaysField.setText(settingsService.get("backup_keep_days", "30"));
        updateNextBackupLabel();
        updateLastBackupLabel();
    }

    @FXML
    private void saveSchedule() {
        String time = backupTimeField.getText().trim();
        try {
            LocalTime.parse(time); // validate format
        } catch (Exception e) {
            AlertUtil.showWarning("Invalid Time", "Please enter time in HH:mm format (e.g. 02:00).");
            return;
        }

        int keepDays = 30;
        try { keepDays = Integer.parseInt(keepDaysField.getText().trim()); }
        catch (NumberFormatException e) {
            AlertUtil.showWarning("Invalid", "Keep days must be a number."); return;
        }

        settingsService.set("backup_auto_time",    time);
        settingsService.set("backup_auto_enabled", autoBackupCheck.isSelected() ? "1" : "0");
        settingsService.set("backup_keep_days",    String.valueOf(keepDays));

        AlertUtil.showInfo("Saved", "Backup schedule saved.\nNext backup: " + getNextBackupTime());
        updateNextBackupLabel();
        updateSchedulerStatus();
    }

    @FXML
    private void onAutoBackupToggled() {
        settingsService.set("backup_auto_enabled", autoBackupCheck.isSelected() ? "1" : "0");
        updateSchedulerStatus();
    }

    @FXML
    private void changeBackupPath() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose Backup Folder");
        File dir = dc.showDialog(backupPathField.getScene().getWindow());
        if (dir != null) {
            backupPathField.setText(dir.getAbsolutePath());
            settingsService.set("backup_path", dir.getAbsolutePath());
        }
    }

    // ── Manual Backup ─────────────────────────────────────────────────────────

    @FXML
    private void backupNow() {
        // Pre-flight: check mysqldump is findable
        String diag = backupService.getMysqldumpDiagnostic();
        if (diag.startsWith("mysqldump NOT")) {
            AlertUtil.showError("mysqldump Not Found",
                diag + "\n\nFix: Install MySQL or add its bin folder to system PATH, " +
                "then set the MySQL Bin Path in Settings.");
            setBackupStatus("✖  mysqldump not found — check Settings", false);
            return;
        }

        backupProgress.setVisible(true);
        backupProgress.setManaged(true);
        backupProgress.setProgress(-1);
        setBackupStatus("⏳  Backing up… (" + diag + ")", null);

        scheduler.runNow();
    }

    // ── Table setup ───────────────────────────────────────────────────────────

    private void setupColumns() {
        colBackupName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        colBackupDate.setCellValueFactory(c -> {
            long ms = c.getValue().lastModified();
            LocalDateTime dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(ms),
                ZoneId.systemDefault());
            return new SimpleStringProperty(dt.format(DT_FMT));
        });

        colBackupSize.setCellValueFactory(c -> {
            long bytes = c.getValue().length();
            String size = bytes < 1024 * 1024
                ? (bytes / 1024) + " KB"
                : new DecimalFormat("#.#").format(bytes / (1024.0 * 1024)) + " MB";
            return new SimpleStringProperty(size);
        });

        colBackupType.setCellValueFactory(c -> {
            String name = c.getValue().getName();
            return new SimpleStringProperty(name.contains("_AUTO_") ? "Auto" : "Manual");
        });
        colBackupType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle("Auto".equals(v)
                    ? "-fx-text-fill:-pos-primary; -fx-font-weight:bold;"
                    : "-fx-text-fill:-pos-text-secondary;");
            }
        });

        colBackupActions.setCellFactory(col -> new TableCell<>() {
            private final Button restoreBtn = new Button("↩ Restore");
            private final Button deleteBtn  = new Button("🗑");
            private final HBox   box        = new HBox(6, restoreBtn, deleteBtn);
            {
                box.setAlignment(Pos.CENTER);
                restoreBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; " +
                    "-fx-background-color:-pos-warning; -fx-text-fill:white; " +
                    "-fx-background-radius:4; -fx-cursor:hand;");
                deleteBtn.setStyle("-fx-font-size:11px; -fx-padding:3 6; " +
                    "-fx-background-color:#FFEBEE; -fx-text-fill:-pos-danger; " +
                    "-fx-background-radius:4; -fx-cursor:hand;");
                restoreBtn.setOnAction(e -> restoreFile(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e  -> deleteBackup(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Most recent first
        colBackupDate.setSortType(TableColumn.SortType.DESCENDING);
    }

    @FXML
    public void refreshHistory() {
        File[] files = backupService.listBackups();
        ObservableList<File> list = FXCollections.observableArrayList(
            files == null ? new File[0] : files);
        historyTable.setItems(list);
        backupCountLabel.setText(list.size() + " backup" + (list.size() == 1 ? "" : "s"));
        backupProgress.setVisible(false);
        backupProgress.setManaged(false);
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    @FXML
    private void restoreFromFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Backup File to Restore");
        fc.setInitialDirectory(new File(scheduler.getBackupDir()));
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("SQL Backup", "*.sql"));
        File file = fc.showOpenDialog(historyTable.getScene().getWindow());
        if (file != null) restoreFile(file);
    }

    private void restoreFile(File file) {
        if (!AlertUtil.confirm("⚠ Restore Database",
                "This will OVERWRITE all current data with:\n" + file.getName() +
                "\n\nThis CANNOT be undone. Are you absolutely sure?")) return;

        // Second confirmation
        if (!AlertUtil.confirm("Final Confirmation",
                "Last chance — ALL current bills, products and settings will be replaced.\nContinue?"))
            return;

        restoreStatusLabel.setText("⏳ Restoring…");
        new Thread(() -> {
            boolean ok = backupService.restore(file);
            Platform.runLater(() -> {
                if (ok) {
                    restoreStatusLabel.setText("✔ Restore complete. Please restart the application.");
                    restoreStatusLabel.setStyle("-fx-text-fill:-pos-success;");
                    AlertUtil.showInfo("Restore Complete",
                        "Database restored from: " + file.getName() +
                        "\n\nPlease close and restart MiniMart POS.");
                } else {
                    restoreStatusLabel.setText("✖ Restore failed. See logs.");
                    restoreStatusLabel.setStyle("-fx-text-fill:-pos-danger;");
                }
            });
        }, "restore-thread").start();
    }

    private void deleteBackup(File file) {
        if (AlertUtil.confirm("Delete Backup",
                "Permanently delete:\n" + file.getName() + "?")) {
            if (file.delete()) {
                refreshHistory();
                AlertUtil.showInfo("Deleted", "Backup file deleted.");
            } else {
                AlertUtil.showError("Error", "Could not delete file.");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateSchedulerStatus() {
        boolean enabled = scheduler.isAutoEnabled();
        schedulerStatusLabel.setText(enabled ? "● Auto backup ON" : "○ Auto backup OFF");
        schedulerStatusLabel.setStyle(enabled
            ? "-fx-text-fill:-pos-success; -fx-font-weight:bold; -fx-font-size:12px;"
            : "-fx-text-fill:-pos-warning; -fx-font-weight:bold; -fx-font-size:12px;");
    }

    private void updateNextBackupLabel() {
        nextBackupLabel.setText("Next backup: " + getNextBackupTime());
    }

    private String getNextBackupTime() {
        if (!scheduler.isAutoEnabled()) return "disabled";
        LocalTime target = scheduler.getConfiguredTime();
        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(target);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return next.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }

    private void updateLastBackupLabel() {
        File[] backups = backupService.listBackups();
        if (backups != null && backups.length > 0) {
            File latest = backups[0]; // already sorted newest first
            long ms = latest.lastModified();
            LocalDateTime dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(ms), ZoneId.systemDefault());
            lastBackupLabel.setText("Last backup: " + dt.format(DT_FMT));
        } else {
            lastBackupLabel.setText("Last backup: Never");
        }
    }

    private void setBackupStatus(String msg, Boolean success) {
        backupStatusLabel.setText(msg);
        if (success == null) {
            backupStatusLabel.setStyle("-fx-text-fill:-pos-text-secondary;");
        } else {
            backupStatusLabel.setStyle(success
                ? "-fx-text-fill:-pos-success; -fx-font-weight:bold;"
                : "-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
        }
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
