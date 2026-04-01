package com.minimartpos.controller.admin;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.AuditLog;
import com.minimartpos.security.SessionManager;
import com.minimartpos.util.DateUtil;
import com.minimartpos.service.SettingsService;
import com.minimartpos.util.SceneManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class AuditLogController implements Initializable {

    private static final Logger logger = LogManager.getLogger(AuditLogController.class);

    @FXML private Label    sidebarUserLabel;
    @FXML private Label    sidebarCompanyLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> actionFilter;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private Label    rowCountLabel;

    @FXML private TableView<AuditLog>            logTable;
    @FXML private TableColumn<AuditLog, String>  colTime;
    @FXML private TableColumn<AuditLog, String>  colUser;
    @FXML private TableColumn<AuditLog, String>  colAction;
    @FXML private TableColumn<AuditLog, String>  colEntity;
    @FXML private TableColumn<AuditLog, String>  colEntityId;
    @FXML private TableColumn<AuditLog, String>  colOld;
    @FXML private TableColumn<AuditLog, String>  colNew;

    private final SettingsService settingsService = new SettingsService();
    private final ObservableList<AuditLog> allRows    = FXCollections.observableArrayList();
    private FilteredList<AuditLog>         filtered;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        sidebarCompanyLabel.setText("🛒 " + settingsService.company());
        fromDate.setValue(LocalDate.now().minusDays(7));
        toDate.setValue(LocalDate.now());
        setupColumns();
        setupActionFilter();
        loadLog();
    }

    private void setupColumns() {
        colTime.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCreatedAt() != null
                ? DateUtil.formatDateTime(c.getValue().getCreatedAt()) : "—"));
        colUser.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getUsername()));
        colAction.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getAction()));
        colAction.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                if (v.contains("DELETE") || v.contains("VOID") || v.contains("DISABLE"))
                    setStyle("-fx-text-fill:-pos-danger;");
                else if (v.contains("CREATE") || v.contains("ENABLE"))
                    setStyle("-fx-text-fill:-pos-success;");
                else
                    setStyle("-fx-text-fill:-pos-text-primary;");
            }
        });
        colEntity.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntityType() != null
                ? c.getValue().getEntityType() : "—"));
        colEntityId.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntityId() > 0
                ? String.valueOf(c.getValue().getEntityId()) : "—"));
        colOld.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getOldValue() != null
                ? c.getValue().getOldValue() : ""));
        colNew.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getNewValue() != null
                ? c.getValue().getNewValue() : ""));
    }

    private void setupActionFilter() {
        // Load distinct actions from DB
        List<String> actions = new ArrayList<>();
        actions.add("All Actions");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT action FROM audit_log ORDER BY action");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) actions.add(rs.getString(1));
        } catch (SQLException e) {
            logger.error("Load action filter: {}", e.getMessage());
        }
        actionFilter.setItems(FXCollections.observableArrayList(actions));
        actionFilter.getSelectionModel().selectFirst();
    }

    @FXML
    public void loadLog() {
        LocalDate from = fromDate.getValue() != null ? fromDate.getValue() : LocalDate.now().minusDays(7);
        LocalDate to   = toDate.getValue()   != null ? toDate.getValue()   : LocalDate.now();

        new Thread(() -> {
            List<AuditLog> rows = queryLog(from, to);
            Platform.runLater(() -> {
                allRows.setAll(rows);
                filtered = new FilteredList<>(allRows, r -> true);
                logTable.setItems(filtered);
                applyFilters();
            });
        }).start();
    }

    private List<AuditLog> queryLog(LocalDate from, LocalDate to) {
        List<AuditLog> list = new ArrayList<>();
        String sql =
            "SELECT a.*, u.username FROM audit_log a " +
            "LEFT JOIN users u ON a.user_id = u.id " +
            "WHERE DATE(a.created_at) BETWEEN ? AND ? " +
            "ORDER BY a.created_at DESC LIMIT 1000";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditLog log = new AuditLog();
                    log.setId(rs.getInt("id"));
                    log.setUserId(rs.getInt("user_id"));
                    log.setUsername(rs.getString("username") != null
                        ? rs.getString("username") : "system");
                    log.setAction(rs.getString("action"));
                    log.setEntityType(rs.getString("entity_type"));
                    log.setEntityId(rs.getInt("entity_id"));
                    log.setOldValue(rs.getString("old_value"));
                    log.setNewValue(rs.getString("new_value"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) log.setCreatedAt(ts.toLocalDateTime());
                    list.add(log);
                }
            }
        } catch (SQLException e) {
            logger.error("queryLog: {}", e.getMessage(), e);
        }
        return list;
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String action = actionFilter.getValue();
        filtered.setPredicate(row -> {
            boolean matchSearch = search.isEmpty()
                || (row.getAction() != null && row.getAction().toLowerCase().contains(search))
                || (row.getUsername() != null && row.getUsername().toLowerCase().contains(search))
                || (row.getEntityType() != null && row.getEntityType().toLowerCase().contains(search));
            boolean matchAction = action == null || action.equals("All Actions")
                || action.equals(row.getAction());
            return matchSearch && matchAction;
        });
        rowCountLabel.setText(filtered.size() + " entries");
    }

    @FXML private void onSearchChanged()  { applyFilters(); }
    @FXML private void onFilterChanged()  { applyFilters(); }

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
