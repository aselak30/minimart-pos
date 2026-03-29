package com.minimartpos.controller.admin;

import com.minimartpos.model.Customer;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.CustomerService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.SceneManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

public class CustomerManagementController implements Initializable {

    private static final Logger logger = LogManager.getLogger(CustomerManagementController.class);

    @FXML private Label     sidebarUserLabel;
    @FXML private TextField searchField;
    @FXML private Label     countLabel;

    @FXML private TableView<Customer>            customerTable;
    @FXML private TableColumn<Customer, String>  colName;
    @FXML private TableColumn<Customer, String>  colPhone;
    @FXML private TableColumn<Customer, String>  colEmail;
    @FXML private TableColumn<Customer, String>  colCredit;
    @FXML private TableColumn<Customer, String>  colLimit;
    @FXML private TableColumn<Customer, String>  colPoints;
    @FXML private TableColumn<Customer, String>  colStatus;
    @FXML private TableColumn<Customer, String>  colActions;

    @FXML private VBox      editPanel;
    @FXML private Label     editTitle;
    @FXML private TextField fieldName;
    @FXML private TextField fieldPhone;
    @FXML private TextField fieldEmail;
    @FXML private TextArea  fieldAddress;
    @FXML private TextField fieldCreditLimit;
    @FXML private Label     creditBalanceLabel;
    @FXML private Label     loyaltyLabel;
    @FXML private TextArea  fieldNotes;
    @FXML private CheckBox  fieldActive;
    @FXML private Label     formError;

    private final CustomerService customerService = new CustomerService();
    private final ObservableList<Customer> allCustomers    = FXCollections.observableArrayList();
    private FilteredList<Customer>         filteredCustomers;
    private Customer                       editingCustomer;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        setupColumns();
        refresh();
    }

    private void setupColumns() {
        colName.setCellValueFactory(c   -> new SimpleStringProperty(c.getValue().getName()));
        colPhone.setCellValueFactory(c  -> new SimpleStringProperty(
            c.getValue().getPhone() != null ? c.getValue().getPhone() : "—"));
        colEmail.setCellValueFactory(c  -> new SimpleStringProperty(
            c.getValue().getEmail() != null ? c.getValue().getEmail() : "—"));
        colCredit.setCellValueFactory(c -> new SimpleStringProperty(
            CurrencyUtil.formatPlain(c.getValue().getCreditBalance())));
        colCredit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                try {
                    double d = Double.parseDouble(v.replace(",", ""));
                    setStyle(d > 0 ? "-fx-text-fill:-pos-warning; -fx-font-weight:bold;" : "");
                } catch (NumberFormatException ignored) { setStyle(""); }
            }
        });
        colLimit.setCellValueFactory(c  -> new SimpleStringProperty(
            CurrencyUtil.formatPlain(c.getValue().getCreditLimit())));
        colPoints.setCellValueFactory(c -> new SimpleStringProperty(
            String.valueOf(c.getValue().getLoyaltyPoints())));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().isActive() ? "Active" : "Inactive"));
        colStatus.setCellFactory(col -> new TableCell<>() {
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
                    Customer c = getTableView().getItems().get(getIndex());
                    final int id = c.getId();
                    final String name = c.getName();
                    if (com.minimartpos.util.AlertUtil.confirm("Delete Customer", "Delete: " + name + "? This cannot be undone.")) {
                        if (new com.minimartpos.repository.CustomerRepository().delete(id)) {
                            com.minimartpos.util.AlertUtil.showInfo("Deleted", "Customer deleted successfully.");
                            refresh();
                        } else {
                            com.minimartpos.util.AlertUtil.showError("Error", "Could not delete customer. They may have existing bills.");
                        }
                    }
                });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
        customerTable.setRowFactory(tv -> {
            TableRow<Customer> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openEditPanel(row.getItem());
            });
            return row;
        });
    }

    @FXML
    public void refresh() {
        allCustomers.setAll(customerService.getAll());
        filteredCustomers = new FilteredList<>(allCustomers, c -> true);
        customerTable.setItems(filteredCustomers);
        applyFilter();
    }

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        filteredCustomers.setPredicate(c -> q.isEmpty()
            || c.getName().toLowerCase().contains(q)
            || (c.getPhone() != null && c.getPhone().contains(q))
            || (c.getEmail() != null && c.getEmail().toLowerCase().contains(q)));
        countLabel.setText(filteredCustomers.size() + " customers");
    }

    @FXML private void onSearchChanged() { applyFilter(); }

    @FXML
    private void openAddCustomer() {
        editingCustomer = null;
        editTitle.setText("Add New Customer");
        clearForm();
        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    private void openEditPanel(Customer c) {
        editingCustomer = c;
        editTitle.setText("Edit Customer");
        fieldName.setText(c.getName());
        fieldPhone.setText(c.getPhone() != null ? c.getPhone() : "");
        fieldEmail.setText(c.getEmail() != null ? c.getEmail() : "");
        fieldAddress.setText(c.getAddress() != null ? c.getAddress() : "");
        fieldCreditLimit.setText(c.getCreditLimit() != null ? c.getCreditLimit().toPlainString() : "0");
        fieldNotes.setText(c.getNotes() != null ? c.getNotes() : "");
        fieldActive.setSelected(c.isActive());
        creditBalanceLabel.setText(CurrencyUtil.format(c.getCreditBalance()));
        loyaltyLabel.setText(c.getLoyaltyPoints() + " pts");
        clearError();
        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    @FXML
    private void saveCustomer() {
        clearError();
        String name = fieldName.getText().trim();
        if (name.isEmpty()) { showError("Name is required."); return; }

        Customer c = editingCustomer != null ? editingCustomer : new Customer();
        c.setName(name);
        c.setPhone(fieldPhone.getText().trim().isEmpty() ? null : fieldPhone.getText().trim());
        c.setEmail(fieldEmail.getText().trim().isEmpty() ? null : fieldEmail.getText().trim());
        c.setAddress(fieldAddress.getText().trim().isEmpty() ? null : fieldAddress.getText().trim());
        c.setNotes(fieldNotes.getText().trim().isEmpty() ? null : fieldNotes.getText().trim());
        c.setActive(fieldActive.isSelected());

        try {
            BigDecimal limit = new BigDecimal(fieldCreditLimit.getText().trim().isEmpty()
                ? "0" : fieldCreditLimit.getText().trim());
            c.setCreditLimit(limit);
        } catch (NumberFormatException e) {
            showError("Invalid credit limit."); return;
        }

        int id = customerService.save(c);
        if (id > 0) {
            AlertUtil.showInfo("Saved", "Customer '" + name + "' saved.");
            refresh();
            closePanel();
        } else {
            showError("Failed to save customer.");
        }
    }


    @FXML private void deleteCustomer() {
        if (editingCustomer == null) return;
        if (!com.minimartpos.util.AlertUtil.confirm("Delete Customer",
                "Delete: " + editingCustomer.getName() + "? This cannot be undone.")) return;
        boolean ok = new com.minimartpos.repository.CustomerRepository().delete(editingCustomer.getId());
        if (ok) {
            com.minimartpos.util.AlertUtil.showInfo("Deleted", "Customer deleted successfully.");
            closePanel();
            refresh();
        } else {
            com.minimartpos.util.AlertUtil.showError("Error", "Could not delete customer. They may have existing bills.");
        }
    }

    @FXML private void closePanel() {
        editPanel.setVisible(false); editPanel.setManaged(false); editingCustomer = null;
    }

    private void clearForm() {
        fieldName.clear(); fieldPhone.clear(); fieldEmail.clear();
        fieldAddress.clear(); fieldCreditLimit.setText("0"); fieldNotes.clear();
        fieldActive.setSelected(true);
        creditBalanceLabel.setText("Rs. 0.00"); loyaltyLabel.setText("0 pts");
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
