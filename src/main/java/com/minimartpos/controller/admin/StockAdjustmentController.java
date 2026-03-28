package com.minimartpos.controller.admin;

import com.minimartpos.model.Product;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.ExcelService;
import com.minimartpos.service.ProductService;
import com.minimartpos.service.StockService;
import com.minimartpos.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class StockAdjustmentController implements Initializable {

    private static final Logger logger = LogManager.getLogger(StockAdjustmentController.class);

    @FXML private Label    sidebarUserLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> stockFilter;
    @FXML private Label    productCountLabel;
    @FXML private Label    lowStockBadge;

    @FXML private TableView<Product>            stockTable;
    @FXML private TableColumn<Product, String>  colBarcode;
    @FXML private TableColumn<Product, String>  colName;
    @FXML private TableColumn<Product, String>  colCategory;
    @FXML private TableColumn<Product, String>  colCurrent;
    @FXML private TableColumn<Product, String>  colReorder;
    @FXML private TableColumn<Product, String>  colStatus;
    @FXML private TableColumn<Product, String>  colActions;

    // Adjustment panel
    @FXML private VBox       adjustPanel;
    @FXML private Label      adjustProductName;
    @FXML private Label      adjustCurrentStock;
    @FXML private ComboBox<String> adjustTypeCombo;
    @FXML private TextField  adjustQtyField;
    @FXML private Label      newStockPreview;
    @FXML private TextArea   adjustNotesField;
    @FXML private Label      adjustErrorLabel;

    private final ProductService productService = new ProductService();
    private final StockService   stockService   = new StockService();
    private final ObservableList<Product> allProducts    = FXCollections.observableArrayList();
    private FilteredList<Product>         filteredProducts;
    private Product                       selectedProduct;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        stockFilter.setItems(FXCollections.observableArrayList(
            "All Products", "Low Stock Only", "Out of Stock"));
        stockFilter.getSelectionModel().selectFirst();
        adjustTypeCombo.setItems(FXCollections.observableArrayList(
            "Add Stock (Purchase/Receive)",
            "Remove Stock (Damage/Loss)",
            "Set Exact Quantity",
            "Return to Supplier"));
        adjustTypeCombo.getSelectionModel().selectFirst();
        setupColumns();
        refreshProducts();
    }

    private void setupColumns() {
        colBarcode.setCellValueFactory(c  -> new SimpleStringProperty(c.getValue().getBarcode()));
        colName.setCellValueFactory(c     -> new SimpleStringProperty(c.getValue().getName()));
        colCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategoryName()));
        colCurrent.setCellValueFactory(c  -> new SimpleStringProperty(c.getValue().getStockQuantity() != null ? c.getValue().getStockQuantity().toString() : "0.000"));
        colCurrent.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                BigDecimal qty = new BigDecimal(v);
                if (qty.compareTo(BigDecimal.ZERO) <= 0)
                    setStyle("-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
                else if (getIndex() < getTableView().getItems().size() &&
                         getTableView().getItems().get(getIndex()).isLowStock())
                    setStyle("-fx-text-fill:-pos-warning; -fx-font-weight:bold;");
                else
                    setStyle("");
            }
        });
        colReorder.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReorderLevel())));
        colStatus.setCellValueFactory(c -> {
            Product p = c.getValue();
            if (p.isOutOfStock())   return new SimpleStringProperty("OUT");
            if (p.isLowStock())     return new SimpleStringProperty("LOW");
            return new SimpleStringProperty("OK");
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle(switch (v) {
                    case "OUT" -> "-fx-text-fill:-pos-danger; -fx-font-weight:bold;";
                    case "LOW" -> "-fx-text-fill:-pos-warning; -fx-font-weight:bold;";
                    default    -> "-fx-text-fill:-pos-success; -fx-font-weight:bold;";
                });
            }
        });
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button adjBtn = new Button("📦 Adjust");
            {
                adjBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                    "-fx-background-color:-pos-primary; -fx-text-fill:white; -fx-background-radius:4;");
                adjBtn.setOnAction(e -> {
                    selectedProduct = getTableView().getItems().get(getIndex());
                    openAdjustPanel(selectedProduct);
                });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : adjBtn);
            }
        });

        stockTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    selectedProduct = row.getItem();
                    openAdjustPanel(selectedProduct);
                }
            });
            return row;
        });
    }

    @FXML
    public void refreshProducts() {
        productService.invalidateCache();
        List<Product> products = productService.getAllActive();
        allProducts.setAll(products);
        filteredProducts = new FilteredList<>(allProducts, p -> true);
        stockTable.setItems(filteredProducts);
        applyFilters();
        long lowCount = products.stream().filter(Product::isLowStock).count();
        lowStockBadge.setText("⚠ " + lowCount + " low stock");
        lowStockBadge.setVisible(lowCount > 0);
        lowStockBadge.setManaged(lowCount > 0);
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String filter = stockFilter.getValue();
        filteredProducts.setPredicate(p -> {
            boolean matchSearch = search.isEmpty()
                || p.getName().toLowerCase().contains(search)
                || p.getBarcode().toLowerCase().contains(search);
            boolean matchFilter = switch (filter == null ? "All Products" : filter) {
                case "Low Stock Only"  -> p.isLowStock();
                case "Out of Stock"    -> p.isOutOfStock();
                default                -> true;
            };
            return matchSearch && matchFilter;
        });
        productCountLabel.setText(filteredProducts.size() + " products");
    }

    @FXML private void onSearchChanged() { applyFilters(); }
    @FXML private void onFilterChanged() { applyFilters(); }

    // ── Adjustment Panel ──────────────────────────────────────────────────────

    private void openAdjustPanel(Product p) {
        adjustProductName.setText(p.getName());
        adjustCurrentStock.setText(String.valueOf(p.getStockQuantity()));
        adjustQtyField.clear();
        adjustNotesField.clear();
        newStockPreview.setText("—");
        clearAdjustError();
        adjustPanel.setVisible(true);
        adjustPanel.setManaged(true);
        adjustQtyField.requestFocus();
    }

    @FXML
    private void onQtyChanged() {
        if (selectedProduct == null) return;
        try {
            BigDecimal qty     = new BigDecimal(adjustQtyField.getText().trim());
            BigDecimal current = selectedProduct.getStockQuantity();
            String type = adjustTypeCombo.getValue();
    
            BigDecimal newQty = switch (type) {
                case "Remove Stock (Damage/Loss)", "Return to Supplier" -> current.subtract(qty);
                case "Set Exact Quantity"                               -> qty;
                default                                                  -> current.add(qty);
            };
    
            newStockPreview.setText(newQty.toString());
            newStockPreview.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-text-fill:" +
                (newQty.compareTo(BigDecimal.ZERO) < 0 ? "-pos-danger;" : newQty.compareTo(selectedProduct.getReorderLevel()) <= 0
                    ? "-pos-warning;" : "-pos-success;"));
            clearAdjustError();
        } catch (NumberFormatException e) {
            newStockPreview.setText("—");
        }
    }

    @FXML
    private void applyAdjustment() {
        if (selectedProduct == null) return;
        clearAdjustError();

        BigDecimal qty;
        try {
            qty = new BigDecimal(adjustQtyField.getText().trim());
            if (qty.compareTo(BigDecimal.ZERO) < 0) { showAdjustError("Quantity must be positive."); return; }
        } catch (NumberFormatException e) {
            showAdjustError("Please enter a valid number."); return;
        }

        String type     = adjustTypeCombo.getValue();
        BigDecimal current  = selectedProduct.getStockQuantity();
        int    userId   = SessionManager.getCurrentUser().getId();
        boolean success;

        success = switch (type) {
            case "Set Exact Quantity" ->
                stockService.manualAdjust(selectedProduct.getId(), qty, userId);
            case "Remove Stock (Damage/Loss)" -> {
                if (qty.compareTo(current) > 0) { showAdjustError("Cannot remove more than current stock."); yield false; }
                yield stockService.manualAdjust(selectedProduct.getId(), current.subtract(qty), userId);
            }
            case "Return to Supplier" -> {
                if (qty.compareTo(current) > 0) { showAdjustError("Cannot return more than current stock."); yield false; }
                yield stockService.manualAdjust(selectedProduct.getId(), current.subtract(qty), userId);
            }
            default -> // Add Stock
                stockService.manualAdjust(selectedProduct.getId(), current.add(qty), userId);
        };

        if (success) {
            AlertUtil.showInfo("Stock Updated",
                selectedProduct.getName() + " stock updated successfully.");
            closeAdjustPanel();
            refreshProducts();
        } else {
            showAdjustError("Failed to update stock. Please try again.");
        }
    }

    @FXML
    private void closeAdjustPanel() {
        adjustPanel.setVisible(false);
        adjustPanel.setManaged(false);
        selectedProduct = null;
    }

    private void showAdjustError(String msg) {
        adjustErrorLabel.setText(msg);
        adjustErrorLabel.setVisible(true);
        adjustErrorLabel.setManaged(true);
    }

    private void clearAdjustError() {
        adjustErrorLabel.setVisible(false);
        adjustErrorLabel.setManaged(false);
    }
@FXML
    private void exportStock() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Stock to Excel");
        fc.setInitialFileName("stock_" + java.time.LocalDate.now() + ".xlsx");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        java.io.File file = fc.showSaveDialog(stockTable.getScene().getWindow());
        if (file == null) return;

        try {
            // Export the currently-visible filtered list (respects search/filter)
            java.util.List<Product> toExport = filteredProducts != null
                ? new java.util.ArrayList<>(filteredProducts)
                : new java.util.ArrayList<>(allProducts);

            new ExcelService().exportStock(toExport, file.getAbsolutePath());

            long low = toExport.stream()
                .filter(p -> p.getStockQuantity().compareTo(BigDecimal.ZERO) > 0 && p.getStockQuantity().compareTo(p.getReorderLevel()) <= 0)
                .count();
            long out = toExport.stream()
                .filter(p -> p.getStockQuantity().compareTo(BigDecimal.ZERO) <= 0)
                .count();

            AlertUtil.showInfo("Stock Exported",
                toExport.size() + " products exported to:\n" + file.getName() +
                "\n\n⚠ Low stock: " + low + "   ✖ Out of stock: " + out +
                "\n\nColour legend: 🟡 Low stock  🔴 Out of stock  ⬜ OK");
        } catch (Exception e) {
            AlertUtil.showError("Export Failed", e.getMessage());
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
