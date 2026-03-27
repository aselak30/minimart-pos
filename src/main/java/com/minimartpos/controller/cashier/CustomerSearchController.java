package com.minimartpos.controller.cashier;

import com.minimartpos.model.Customer;
import com.minimartpos.service.CustomerService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;

/**
 * Modal controller for searching and selecting a customer at the POS.
 * On select, calls back to POSTerminalController.setCustomer(id, name).
 */
public class CustomerSearchController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ListView<Customer> customerList;
    @FXML private VBox    addCustomerForm;
    @FXML private TextField newName;
    @FXML private TextField newPhone;

    private final CustomerService customerService = new CustomerService();
    private BiConsumer<Integer, String> onSelect;  // callback: (customerId, customerName)

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupList();
        loadAll();
    }

    /** Called by POSTerminalController before showing. */
    public void setOnSelect(BiConsumer<Integer, String> callback) {
        this.onSelect = callback;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @FXML
    private void onSearchChanged() {
        String q = searchField.getText().trim();
        new Thread(() -> {
            List<Customer> results = q.isEmpty()
                ? customerService.getAll()
                : customerService.search(q);
            Platform.runLater(() ->
                customerList.setItems(FXCollections.observableArrayList(results)));
        }).start();
    }

    private void loadAll() {
        new Thread(() -> {
            List<Customer> all = customerService.getAll();
            Platform.runLater(() ->
                customerList.setItems(FXCollections.observableArrayList(all)));
        }).start();
    }

    private void setupList() {
        customerList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Customer c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setText(null); setGraphic(null); return; }
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                VBox info = new VBox(2);
                Label name  = new Label(c.getName());
                name.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");
                String sub = (c.getPhone() != null ? c.getPhone() : "—");
                Label phone = new Label("📞 " + sub);
                phone.setStyle("-fx-font-size:11px; -fx-text-fill:-pos-text-secondary;");
                info.getChildren().addAll(name, phone);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                VBox credit = new VBox(2);
                credit.setAlignment(Pos.CENTER_RIGHT);
                if (c.getCreditBalance() != null && c.getCreditBalance().doubleValue() > 0) {
                    Label bal = new Label("Credit: " + CurrencyUtil.format(c.getCreditBalance()));
                    bal.setStyle("-fx-font-size:11px; -fx-text-fill:-pos-warning;");
                    credit.getChildren().add(bal);
                }
                row.getChildren().addAll(info, spacer, credit);
                setGraphic(row);
                setOnMouseClicked(e -> {
                    if (e.getClickCount() >= 1 && c != null) selectCustomer(c);
                });
            }
        });
    }

    // ── Select / Walk-in ──────────────────────────────────────────────────────

    private void selectCustomer(Customer c) {
        if (onSelect != null) onSelect.accept(c.getId(), c.getName());
        close();
    }

    @FXML
    private void selectWalkin() {
        if (onSelect != null) onSelect.accept(0, "Walk-in");
        close();
    }

    // ── Add New Customer ──────────────────────────────────────────────────────

    @FXML
    private void openAddCustomer() {
        addCustomerForm.setVisible(true);
        addCustomerForm.setManaged(true);
        newName.requestFocus();
    }

    @FXML
    private void cancelAddCustomer() {
        addCustomerForm.setVisible(false);
        addCustomerForm.setManaged(false);
        newName.clear(); newPhone.clear();
    }

    @FXML
    private void saveNewCustomer() {
        String name = newName.getText().trim();
        if (name.isEmpty()) { AlertUtil.showWarning("Required", "Name is required."); return; }

        Customer c = new Customer();
        c.setName(name);
        c.setPhone(newPhone.getText().trim());

        int id = customerService.save(c);
        if (id > 0) {
            cancelAddCustomer();
            loadAll();
            // Auto-select the new customer
            if (onSelect != null) onSelect.accept(id, name);
            close();
        } else {
            AlertUtil.showError("Error", "Failed to save customer.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FXML private void cancel() { close(); }

    private void close() {
        Stage stage = (Stage) searchField.getScene().getWindow();
        stage.close();
    }
}
