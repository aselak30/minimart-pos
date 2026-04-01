package com.minimartpos.controller.admin;

import com.minimartpos.model.Supplier;
import com.minimartpos.repository.SupplierRepository;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.AuditService;
import com.minimartpos.service.SettingsService;
import com.minimartpos.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class SupplierManagementController implements Initializable {

    @FXML private Label     sidebarUserLabel;
    @FXML private Label     sidebarCompanyLabel;
    @FXML private TextField searchField;
    @FXML private Label     countLabel;

    @FXML private TableView<Supplier>            supplierTable;
    @FXML private TableColumn<Supplier, String>  colName;
    @FXML private TableColumn<Supplier, String>  colContact;
    @FXML private TableColumn<Supplier, String>  colPhone;
    @FXML private TableColumn<Supplier, String>  colEmail;
    @FXML private TableColumn<Supplier, String>  colActive;
    @FXML private TableColumn<Supplier, String>  colActions;

    @FXML private VBox      editPanel;
    @FXML private Label     editTitle;
    @FXML private TextField fieldName;
    @FXML private TextField fieldContact;
    @FXML private TextField fieldPhone;
    @FXML private TextField fieldEmail;
    @FXML private TextArea  fieldAddress;
    @FXML private CheckBox  fieldActive;
    @FXML private Label     formError;

    private final SupplierRepository supplierRepo = new SupplierRepository();
    private final SettingsService    settingsService = new SettingsService();
    private final AuditService       auditService = new AuditService();
    private final ObservableList<Supplier> allSuppliers    = FXCollections.observableArrayList();
    private FilteredList<Supplier>         filteredSuppliers;
    private Supplier                       editingSupplier;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        sidebarCompanyLabel.setText("🛒 " + settingsService.company());
        setupColumns();
        refresh();
    }

    private void setupColumns() {
        colName.setCellValueFactory(c    -> new SimpleStringProperty(c.getValue().getName()));
        colContact.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getContactName() != null ? c.getValue().getContactName() : "—"));
        colPhone.setCellValueFactory(c   -> new SimpleStringProperty(
            c.getValue().getPhone() != null ? c.getValue().getPhone() : "—"));
        colEmail.setCellValueFactory(c   -> new SimpleStringProperty(
            c.getValue().getEmail() != null ? c.getValue().getEmail() : "—"));
        colActive.setCellValueFactory(c  -> new SimpleStringProperty(
            c.getValue().isActive() ? "Active" : "Inactive"));
        colActive.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle(v.equals("Active")
                    ? "-fx-text-fill:-pos-success; -fx-font-weight:bold;"
                    : "-fx-text-fill:-pos-danger;");
            }
        });
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏");
            private final Button delBtn  = new Button("🗑");
            private final HBox box = new HBox(4, editBtn, delBtn);
            { 
                box.setAlignment(Pos.CENTER);
                editBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                    "-fx-background-color:-pos-primary; -fx-text-fill:white; -fx-background-radius:4;");
                delBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                    "-fx-background-color:#FFEBEE; -fx-text-fill:-pos-danger; -fx-background-radius:4;");

                editBtn.setOnAction(e -> openEditPanel(getTableView().getItems().get(getIndex())));
                delBtn.setOnAction(e -> {
                    Supplier s = getTableView().getItems().get(getIndex());
                    final int id = s.getId();
                    final String name = s.getName();
                    if (com.minimartpos.util.AlertUtil.confirm("Delete Supplier", "Delete: " + name + "? This cannot be undone.")) {
                        if (new com.minimartpos.repository.SupplierRepository().delete(id)) {
                            com.minimartpos.util.AlertUtil.showInfo("Deleted", "Supplier deleted successfully.");
                            refresh();
                        } else {
                            com.minimartpos.util.AlertUtil.showError("Error", "Could not delete supplier.");
                        }
                    }
                });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
        supplierTable.setRowFactory(tv -> {
            TableRow<Supplier> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openEditPanel(row.getItem());
            });
            return row;
        });
    }

    @FXML
    public void refresh() {
        allSuppliers.setAll(supplierRepo.findAllActive());
        filteredSuppliers = new FilteredList<>(allSuppliers, s -> true);
        supplierTable.setItems(filteredSuppliers);
        applyFilter();
    }

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        filteredSuppliers.setPredicate(s -> q.isEmpty()
            || s.getName().toLowerCase().contains(q)
            || (s.getContactName() != null && s.getContactName().toLowerCase().contains(q))
            || (s.getPhone() != null && s.getPhone().contains(q)));
        countLabel.setText(filteredSuppliers.size() + " suppliers");
    }

    @FXML private void onSearchChanged() { applyFilter(); }

    @FXML
    private void openAddSupplier() {
        editingSupplier = null;
        editTitle.setText("Add New Supplier");
        clearForm();
        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    private void openEditPanel(Supplier s) {
        editingSupplier = s;
        editTitle.setText("Edit Supplier");
        fieldName.setText(s.getName());
        fieldContact.setText(s.getContactName() != null ? s.getContactName() : "");
        fieldPhone.setText(s.getPhone() != null ? s.getPhone() : "");
        fieldEmail.setText(s.getEmail() != null ? s.getEmail() : "");
        fieldAddress.setText(s.getAddress() != null ? s.getAddress() : "");
        fieldActive.setSelected(s.isActive());
        clearError();
        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    @FXML
    private void saveSupplier() {
        clearError();
        String name = fieldName.getText().trim();
        if (name.isEmpty()) { showError("Supplier name is required."); return; }

        Supplier s = editingSupplier != null ? editingSupplier : new Supplier();
        s.setName(name);
        s.setContactName(fieldContact.getText().trim().isEmpty() ? null : fieldContact.getText().trim());
        s.setPhone(fieldPhone.getText().trim().isEmpty() ? null : fieldPhone.getText().trim());
        s.setEmail(fieldEmail.getText().trim().isEmpty() ? null : fieldEmail.getText().trim());
        s.setAddress(fieldAddress.getText().trim().isEmpty() ? null : fieldAddress.getText().trim());
        s.setActive(fieldActive.isSelected());

        boolean isNew = (editingSupplier == null);
        int id = isNew ? supplierRepo.insert(s) : (supplierRepo.update(s) ? s.getId() : -1);

        if (id > 0) {
            auditService.log(isNew ? "SUPPLIER_CREATE" : "SUPPLIER_UPDATE",
                "suppliers", id, null, "name=" + name);
            AlertUtil.showInfo("Saved", "Supplier '" + name + "' saved.");
            refresh();
            closePanel();
        } else {
            showError("Failed to save supplier.");
        }
    }

    
    @FXML private void deleteSupplier() {
        if (editingSupplier == null) return;
        if (!com.minimartpos.util.AlertUtil.confirm("Delete Supplier",
                "Delete: " + editingSupplier.getName() + "? This cannot be undone.")) return;
        boolean ok = new com.minimartpos.repository.SupplierRepository().delete(editingSupplier.getId());
        if (ok) {
            com.minimartpos.util.AlertUtil.showInfo("Deleted", "Supplier deleted successfully.");
            closePanel();
            refresh();
        } else {
            com.minimartpos.util.AlertUtil.showError("Error", "Could not delete supplier.");
        }
    }

    @FXML private void closePanel() {
        editPanel.setVisible(false); editPanel.setManaged(false); editingSupplier = null;
    }

    private void clearForm() {
        fieldName.clear(); fieldContact.clear(); fieldPhone.clear();
        fieldEmail.clear(); fieldAddress.clear(); fieldActive.setSelected(true);
        clearError();
    }

    private void showError(String msg) {
        formError.setText(msg); formError.setVisible(true); formError.setManaged(true);
    }
    private void clearError() {
        formError.setVisible(false); formError.setManaged(false);
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
