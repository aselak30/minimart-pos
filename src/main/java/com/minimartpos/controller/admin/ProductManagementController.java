package com.minimartpos.controller.admin;

import com.minimartpos.model.Category;
import com.minimartpos.model.Product;
import com.minimartpos.model.Supplier;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.ExcelService;
import com.minimartpos.service.ProductService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import com.minimartpos.util.BarcodeUtil;
import com.minimartpos.util.ValidationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Admin screen for managing products.
 *
 * Features:
 *  - Searchable + filterable product table (category, status)
 *  - Inline edit panel with full product form
 *  - Live margin/profit preview while editing prices
 *  - Barcode generation (random UUID-based EAN-13 style)
 *  - Add category inline
 *  - Import from Excel (Apache POI — skeleton hooked up)
 *  - Export to Excel
 *  - Activate / Deactivate products
 *  - Low-stock badge in header
 */
public class ProductManagementController implements Initializable {

    private static final Logger logger = LogManager.getLogger(ProductManagementController.class);

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private Label  sidebarUserLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String>   categoryFilter;
    @FXML private ComboBox<String>   statusFilter;
    @FXML private Label  productCountLabel;
    @FXML private Label  lowStockBadge;

    // Table
    @FXML private TableView<Product>            productTable;
    @FXML private TableColumn<Product, String>  colBarcode;
    @FXML private TableColumn<Product, String>  colName;
    @FXML private TableColumn<Product, String>  colCategory;
    @FXML private TableColumn<Product, String>  colPrice;
    @FXML private TableColumn<Product, String>  colCost;
    @FXML private TableColumn<Product, String>  colStock;
    @FXML private TableColumn<Product, String>  colReorder;
    @FXML private TableColumn<Product, String>  colExpiry;
    @FXML private TableColumn<Product, String>  colStatus;
    @FXML private TableColumn<Product, String>  colActions;

    // Edit panel
    @FXML private VBox        editPanel;
    @FXML private Label       editPanelTitle;
    @FXML private TextField   fieldBarcode;
    @FXML private TextField   fieldName;
    @FXML private TextField   fieldBrand;
    @FXML private TextField   fieldSize;
    @FXML private ComboBox<Category>  fieldCategory;
    @FXML private ComboBox<Supplier>  fieldSupplier;
    @FXML private TextField   fieldPrice;
    @FXML private TextField   fieldCost;
    @FXML private Label       marginPreviewLabel;
    @FXML private Label       profitPreviewLabel;
    @FXML private TextField   fieldTax;
    @FXML private TextField   fieldMaxDiscount;
    @FXML private CheckBox    fieldDiscountAllowed;
    @FXML private TextField   fieldStock;
    @FXML private TextField   fieldReorder;
    @FXML private CheckBox    fieldIsWeightBased;
    @FXML private VBox        weightSettingsBox;
    @FXML private ComboBox<String> fieldWeightUnit;
    @FXML private TextField   fieldPricePerUnit;
    @FXML private TextField   fieldDefaultWeight;
    @FXML private TextField   fieldMinWeight;
    @FXML private TextField   fieldMaxWeight;
    @FXML private DatePicker  fieldExpiry;
    @FXML private TextField   fieldBatch;
    @FXML private TextField   fieldLocation;
    @FXML private CheckBox    fieldActive;
    @FXML private Label       formErrorLabel;
    @FXML private Button      deactivateBtn;
    @FXML private Button      saveBtn;

    // ── State ─────────────────────────────────────────────────────────────────
    private final ProductService            productService = new ProductService();
    private final ObservableList<Product>   allProducts    = FXCollections.observableArrayList();
    private FilteredList<Product>           filteredProducts;
    private Product                         editingProduct = null;

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        sidebarUserLabel.setText(SessionManager.getCurrentUser().getFullName());
        setupFilters();
        setupTableColumns();
        loadReferenceData();
        refreshProducts();
        logger.info("ProductManagement screen initialized");
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
            "Active", "Inactive", "All"));
        statusFilter.getSelectionModel().selectFirst();
        fieldWeightUnit.setItems(FXCollections.observableArrayList("kg", "g", "pcs", "ltr", "ml"));
    }

    private void setupTableColumns() {
        colBarcode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBarcode()));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategoryName()));

        colPrice.setCellValueFactory(c ->
            new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getUnitPrice())));

        colCost.setCellValueFactory(c ->
            new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getCostPrice())));

        colStock.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getStockQuantity() != null ? c.getValue().getStockQuantity().toString() : "0"));
        colStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                BigDecimal qty = new BigDecimal(v);
                if (qty.compareTo(BigDecimal.ZERO) <= 0)
                    setStyle("-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
                else if (getTableView() != null && getIndex() < getTableView().getItems().size()) {
                    Product p = getTableView().getItems().get(getIndex());
                    setStyle(p.isLowStock()
                        ? "-fx-text-fill:-pos-warning; -fx-font-weight:bold;"
                        : "");
                }
            }
        });

        colReorder.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getReorderLevel() != null ? c.getValue().getReorderLevel().toString() : "0"));

        colExpiry.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getExpiryDate() != null
                ? DateUtil.formatDate(c.getValue().getExpiryDate()) : "—"));
        colExpiry.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                if (!v.equals("—") && getIndex() < getTableView().getItems().size()) {
                    Product p = getTableView().getItems().get(getIndex());
                    if (p.isExpired())
                        setStyle("-fx-text-fill:-pos-danger; -fx-font-weight:bold;");
                    else if (p.isExpiringSoon(7))
                        setStyle("-fx-text-fill:-pos-warning;");
                    else
                        setStyle("");
                }
            }
        });

        colStatus.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().isActive() ? "Active" : "Inactive"));
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
            private final Button editBtn = new Button("✏ Edit");
            private final HBox box = new HBox(4, editBtn);
            {
                box.setAlignment(Pos.CENTER);
                editBtn.setStyle("-fx-font-size:11px; -fx-padding:3 8; -fx-cursor:hand; " +
                    "-fx-background-color:-pos-primary; -fx-text-fill:white; -fx-background-radius:4;");
                editBtn.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    openEditPanel(p);
                });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Double-click to edit
        productTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    openEditPanel(row.getItem());
            });
            return row;
        });
    }

    private void loadReferenceData() {
        List<Category> categories = productService.getAllCategories();
        List<Supplier> suppliers  = productService.getAllSuppliers();

        // Category filter combo
        ObservableList<String> catNames = FXCollections.observableArrayList("All Categories");
        categories.forEach(c -> catNames.add(c.getName()));
        categoryFilter.setItems(catNames);
        categoryFilter.getSelectionModel().selectFirst();

        // Edit panel combos
        fieldCategory.setItems(FXCollections.observableArrayList(categories));
        fieldSupplier.setItems(FXCollections.observableArrayList(suppliers));
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    @FXML
    public void refreshProducts() {
        // Load all (including inactive) for admin view
        List<Product> products = productService.getAllActive();
        allProducts.setAll(products);
        filteredProducts = new FilteredList<>(allProducts, p -> true);
        productTable.setItems(filteredProducts);
        applyFilters();
        // Low stock badge
        long lowCount = products.stream().filter(Product::isLowStock).count();
        lowStockBadge.setText("⚠ " + lowCount + " low stock");
        lowStockBadge.setVisible(lowCount > 0);
        lowStockBadge.setManaged(lowCount > 0);
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String cat    = categoryFilter.getValue();
        String status = statusFilter.getValue();

        filteredProducts.setPredicate(p -> {
            boolean matchSearch = search.isEmpty()
                || p.getName().toLowerCase().contains(search)
                || p.getBarcode().toLowerCase().contains(search)
                || (p.getBrand() != null && p.getBrand().toLowerCase().contains(search));
            boolean matchCat = cat == null || cat.equals("All Categories")
                || (p.getCategoryName() != null && p.getCategoryName().equals(cat));
            boolean matchStatus = switch (status == null ? "Active" : status) {
                case "Inactive" -> !p.isActive();
                case "All"      -> true;
                default         -> p.isActive();
            };
            return matchSearch && matchCat && matchStatus;
        });
        productCountLabel.setText(filteredProducts.size() + " product" +
                                  (filteredProducts.size() == 1 ? "" : "s"));
    }

    @FXML private void onSearchChanged()         { applyFilters(); }
    @FXML private void onCategoryFilterChanged() { applyFilters(); }
    @FXML private void onStatusFilterChanged()   { applyFilters(); }

    // ── Edit Panel ────────────────────────────────────────────────────────────

    private void openEditPanel(Product p) {
        editingProduct = p;
        editPanelTitle.setText("Edit Product");
        populateForm(p);
        deactivateBtn.setVisible(true);
        deactivateBtn.setManaged(true);
        deactivateBtn.setText(p.isActive() ? "🚫 Deactivate" : "✅ Activate");
        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    @FXML
    private void openAddProduct() {
        editingProduct = null;
        editPanelTitle.setText("Add New Product");
        clearForm();
        deactivateBtn.setVisible(false);
        deactivateBtn.setManaged(false);
        editPanel.setVisible(true);
        editPanel.setManaged(true);
    }

    @FXML
    private void closeEditPanel() {
        editPanel.setVisible(false);
        editPanel.setManaged(false);
        editingProduct = null;
        clearFormError();
    }

    private void populateForm(Product p) {
        fieldBarcode.setText(p.getBarcode());
        fieldName.setText(p.getName());
        fieldBrand.setText(p.getBrand() != null ? p.getBrand() : "");
        fieldSize.setText(p.getSizeWeight() != null ? p.getSizeWeight() : "");
        fieldPrice.setText(p.getUnitPrice() != null ? p.getUnitPrice().toPlainString() : "");
        fieldCost.setText(p.getCostPrice() != null ? p.getCostPrice().toPlainString() : "");
        fieldTax.setText(p.getTaxRate() != null ? p.getTaxRate().toPlainString() : "0");
        fieldMaxDiscount.setText(p.getMaxDiscountPercent() != null
            ? p.getMaxDiscountPercent().toPlainString() : "");
        fieldDiscountAllowed.setSelected(p.isDiscountAllowed());
        fieldStock.setText(p.getStockQuantity() != null ? p.getStockQuantity().toString() : "0");
        fieldReorder.setText(p.getReorderLevel() != null ? p.getReorderLevel().toString() : "5");
        
        fieldIsWeightBased.setSelected(p.isWeightBased());
        onWeightBasedToggle();
        fieldWeightUnit.setValue(p.getWeightUnit());
        fieldPricePerUnit.setText(p.getPricePerUnit() != null ? p.getPricePerUnit().toString() : "");
        fieldDefaultWeight.setText(p.getDefaultWeight() != null ? p.getDefaultWeight().toString() : "");
        fieldMinWeight.setText(p.getMinWeight() != null ? p.getMinWeight().toString() : "");
        fieldMaxWeight.setText(p.getMaxWeight() != null ? p.getMaxWeight().toString() : "");

        fieldExpiry.setValue(p.getExpiryDate());
        fieldBatch.setText(p.getBatchNumber() != null ? p.getBatchNumber() : "");
        fieldLocation.setText(p.getLocation() != null ? p.getLocation() : "");
        fieldActive.setSelected(p.isActive());

        // Set category combo
        fieldCategory.getItems().stream()
            .filter(c -> c.getId() == p.getCategoryId())
            .findFirst().ifPresent(c -> fieldCategory.setValue(c));

        updateMarginPreview();
        clearFormError();
    }

    private void clearForm() {
        fieldBarcode.clear(); fieldName.clear(); fieldBrand.clear(); fieldSize.clear();
        fieldPrice.clear(); fieldCost.clear(); fieldTax.setText("0");
        fieldMaxDiscount.clear(); fieldDiscountAllowed.setSelected(true);
        fieldStock.setText("0"); fieldReorder.setText("5");
        fieldIsWeightBased.setSelected(false);
        onWeightBasedToggle();
        fieldWeightUnit.setValue(null);
        fieldPricePerUnit.clear();
        fieldDefaultWeight.clear();
        fieldMinWeight.clear();
        fieldMaxWeight.clear();
        fieldExpiry.setValue(null); fieldBatch.clear(); fieldLocation.clear();
        fieldActive.setSelected(true);
        fieldCategory.setValue(null); fieldSupplier.setValue(null);
        marginPreviewLabel.setText("—"); profitPreviewLabel.setText("—");
        clearFormError();
    }

    // ── Live Margin Preview ───────────────────────────────────────────────────

    @FXML
    private void updateMarginPreview() {
        BigDecimal price = CurrencyUtil.parse(fieldPrice.getText());
        BigDecimal cost  = CurrencyUtil.parse(fieldCost.getText());
        if (price.compareTo(BigDecimal.ZERO) > 0 && cost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profit = price.subtract(cost);
            BigDecimal margin = profit.divide(price, 4, RoundingMode.HALF_UP)
                                     .multiply(BigDecimal.valueOf(100))
                                     .setScale(1, RoundingMode.HALF_UP);
            marginPreviewLabel.setText(margin.toPlainString() + "%");
            profitPreviewLabel.setText(CurrencyUtil.format(profit));
            marginPreviewLabel.setStyle("-fx-font-weight:bold; -fx-font-size:13px; " +
                (margin.compareTo(BigDecimal.ZERO) > 0
                    ? "-fx-text-fill:-pos-success;" : "-fx-text-fill:-pos-danger;"));
        } else {
            marginPreviewLabel.setText("—");
            profitPreviewLabel.setText("—");
        }
    }

    // ── Barcode Generation ────────────────────────────────────────────────────

    @FXML
    private void generateBarcode() {
        String barcode = com.minimartpos.util.BarcodeUtil.generateEAN13();
        fieldBarcode.setText(barcode);
    }

    // ── Add Category ──────────────────────────────────────────────────────────

    @FXML
    private void addCategory() {
        String name = AlertUtil.promptText("New Category", "Category name:", "");
        if (name.isEmpty()) return;
        int id = productService.addCategory(name);
        if (id > 0) {
            Category newCat = new Category(id, name);
            fieldCategory.getItems().add(newCat);
            fieldCategory.setValue(newCat);
            // Also update filter combo
            categoryFilter.getItems().add(name);
        }
    }

    @FXML
    private void onWeightBasedToggle() {
        boolean isWeight = fieldIsWeightBased.isSelected();
        weightSettingsBox.setVisible(isWeight);
        weightSettingsBox.setManaged(isWeight);
    }

    // ── Save / Deactivate ─────────────────────────────────────────────────────

    @FXML
    private void saveProduct() {
        clearFormError();

        String barcode = fieldBarcode.getText().trim();
        String name    = fieldName.getText().trim();
        Category cat   = fieldCategory.getValue();

        if (com.minimartpos.util.ValidationUtil.isNullOrBlank(barcode)) { showFormError("Barcode is required."); return; }
        if (com.minimartpos.util.ValidationUtil.isNullOrBlank(name))    { showFormError("Product name is required."); return; }
        if (cat == null)                           { showFormError("Please select a category."); return; }

        BigDecimal price = CurrencyUtil.parse(fieldPrice.getText());
        BigDecimal cost  = CurrencyUtil.parse(fieldCost.getText());
        if (!com.minimartpos.util.ValidationUtil.isPositive(price))     { showFormError("Selling price must be greater than 0."); return; }
        if (!com.minimartpos.util.ValidationUtil.isNonNegative(cost))   { showFormError("Cost price cannot be negative."); return; }

        BigDecimal stock   = parseBigDecimalSafe(fieldStock.getText(), BigDecimal.ZERO);
        BigDecimal reorder = parseBigDecimalSafe(fieldReorder.getText(), BigDecimal.valueOf(5));

        Product p = editingProduct != null ? editingProduct : new Product();
        p.setBarcode(barcode);
        p.setName(name);
        p.setBrand(com.minimartpos.util.ValidationUtil.trimOrNull(fieldBrand.getText()));
        p.setSizeWeight(com.minimartpos.util.ValidationUtil.trimOrNull(fieldSize.getText()));
        p.setCategoryId(cat.getId());
        p.setCategoryName(cat.getName());
        p.setUnitPrice(price);
        p.setCostPrice(cost);
        p.setTaxRate(CurrencyUtil.parse(fieldTax.getText()));
        p.setDiscountAllowed(fieldDiscountAllowed.isSelected());
        BigDecimal maxDisc = CurrencyUtil.parse(fieldMaxDiscount.getText());
        p.setMaxDiscountPercent(maxDisc.compareTo(BigDecimal.ZERO) > 0 ? maxDisc : null);
        p.setStockQuantity(stock);
        p.setReorderLevel(reorder);

        p.setWeightBased(fieldIsWeightBased.isSelected());
        if (p.isWeightBased()) {
            p.setWeightUnit(fieldWeightUnit.getValue());
            p.setPricePerUnit(parseBigDecimalSafe(fieldPricePerUnit.getText(), null));
            p.setDefaultWeight(parseBigDecimalSafe(fieldDefaultWeight.getText(), BigDecimal.ONE));
            p.setMinWeight(parseBigDecimalSafe(fieldMinWeight.getText(), BigDecimal.valueOf(0.001)));
            p.setMaxWeight(parseBigDecimalSafe(fieldMaxWeight.getText(), BigDecimal.valueOf(50)));
        }

        p.setExpiryDate(fieldExpiry.getValue());
        p.setBatchNumber(com.minimartpos.util.ValidationUtil.trimOrNull(fieldBatch.getText()));
        p.setLocation(com.minimartpos.util.ValidationUtil.trimOrNull(fieldLocation.getText()));
        p.setActive(fieldActive.isSelected());

        Supplier supplier = fieldSupplier.getValue();
        if (supplier != null) p.setSupplierId(supplier.getId());

        int saved = productService.save(p);
        if (saved > 0) {
            AlertUtil.showInfo("Saved", "Product '" + name + "' saved successfully.");
            productService.invalidateCache();
            refreshProducts();
            closeEditPanel();
        } else {
            showFormError("Failed to save. Barcode may already exist.");
        }
    }

    @FXML
    private void duplicateProduct() {
        if (editingProduct == null || editingProduct.getId() == 0) return;

        // Create a deep copy — clear id, barcode, and mark as new
        Product copy = new Product();
        copy.setName(editingProduct.getName() + " (Copy)");
        copy.setCategoryId(editingProduct.getCategoryId());
        copy.setCategoryName(editingProduct.getCategoryName());
        copy.setBrand(editingProduct.getBrand());
        copy.setSizeWeight(editingProduct.getSizeWeight());
        copy.setUnitPrice(editingProduct.getUnitPrice());
        copy.setCostPrice(editingProduct.getCostPrice());
        copy.setTaxRate(editingProduct.getTaxRate());
        copy.setDiscountAllowed(editingProduct.isDiscountAllowed());
        copy.setMaxDiscountPercent(editingProduct.getMaxDiscountPercent());
        copy.setStockQuantity(BigDecimal.ZERO);                    // fresh stock = 0
        copy.setReorderLevel(editingProduct.getReorderLevel());
        copy.setSupplierId(editingProduct.getSupplierId());
        copy.setSupplierName(editingProduct.getSupplierName());
        copy.setLocation(editingProduct.getLocation());
        copy.setDescription(editingProduct.getDescription());
        copy.setActive(true);
        // Generate a new barcode for the copy
        copy.setBarcode(com.minimartpos.util.BarcodeUtil.generateEAN13());

        // Open the edit panel pre-populated with the copy (id=0 → will INSERT on save)
        editingProduct = null;   // treat as new
        editPanelTitle.setText("Duplicate: " + copy.getName());
        populateForm(copy);
        deactivateBtn.setVisible(false);
        deactivateBtn.setManaged(false);
        editPanel.setVisible(true);
        editPanel.setManaged(true);
        setStatus("ℹ Edit the duplicated product, then click Save.");
    }
    @FXML
    private void deactivateProduct() {
        if (editingProduct == null) return;
        boolean newState = !editingProduct.isActive();
        String action = newState ? "activate" : "deactivate";
        if (AlertUtil.confirm((newState ? "Activate" : "Deactivate") + " Product",
                "Are you sure you want to " + action + " '" + editingProduct.getName() + "'?")) {
            productService.setActive(editingProduct.getId(), newState);
            refreshProducts();
            closeEditPanel();
        }
    }

    @FXML
    private void showPriceHistory() {
        if (editingProduct == null || editingProduct.getId() == 0) {
            AlertUtil.showInfo("Price History", "Save the product first to view its price history.");
            return;
        }

        java.util.List<com.minimartpos.model.PriceHistoryEntry> history =
            productService.getPriceHistory(editingProduct.getId());

        // Build a custom dialog with a TableView
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Price History — " + editingProduct.getName());
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(720);
        dialog.getDialogPane().setPrefHeight(420);

        if (history.isEmpty()) {
            dialog.getDialogPane().setContent(
                new javafx.scene.control.Label("No price changes recorded yet."));
            dialog.showAndWait();
            return;
        }

        javafx.scene.control.TableView<com.minimartpos.model.PriceHistoryEntry> table =
            new javafx.scene.control.TableView<>();
        table.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(javafx.collections.FXCollections.observableArrayList(history));

        addHistoryCol(table, "Date / Time",  180, e ->
            e.getChangedAt() != null
                ? com.minimartpos.util.DateUtil.formatDateTime(e.getChangedAt()) : "—");
        addHistoryCol(table, "Old Price",     100, e ->
            com.minimartpos.util.CurrencyUtil.format(e.getOldPrice()));
        addHistoryCol(table, "New Price",     100, e ->
            com.minimartpos.util.CurrencyUtil.format(e.getNewPrice()));
        addHistoryCol(table, "Change",         85, e -> {
            java.math.BigDecimal pct = e.priceDeltaPct();
            String sign = pct.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "▲ +" : "▼ ";
            return sign + pct.toPlainString() + "%";
        });
        addHistoryCol(table, "Old Cost",       95, e ->
            e.getOldCost() != null
                ? com.minimartpos.util.CurrencyUtil.format(e.getOldCost()) : "—");
        addHistoryCol(table, "New Cost",       95, e ->
            e.getNewCost() != null
                ? com.minimartpos.util.CurrencyUtil.format(e.getNewCost()) : "—");
        addHistoryCol(table, "Changed By",    110, e ->
            e.getChangedByName() != null ? e.getChangedByName() : "—");
        addHistoryCol(table, "Reason",        160, e ->
            e.getReason() != null ? e.getReason() : "—");

        // Colour rows: increase = red tint, decrease = green tint
        table.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(
                    com.minimartpos.model.PriceHistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(item.isPriceIncrease()
                    ? "-fx-background-color: #FFEBEE;"
                    : "-fx-background-color: #E8F5E9;");
            }
        });

        dialog.getDialogPane().setContent(table);
        dialog.showAndWait();
    }

    @SuppressWarnings("unchecked")
    private void addHistoryCol(
            javafx.scene.control.TableView<com.minimartpos.model.PriceHistoryEntry> table,
            String header, double width,
            java.util.function.Function<
                com.minimartpos.model.PriceHistoryEntry, String> fn) {
        javafx.scene.control.TableColumn<
            com.minimartpos.model.PriceHistoryEntry, String> col =
                new javafx.scene.control.TableColumn<>(header);
        col.setPrefWidth(width);
        col.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(fn.apply(c.getValue())));
        table.getColumns().add(col);
    }

    // ── Excel Import / Export ─────────────────────────────────────────────────

    @FXML
    private void downloadTemplate() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Import Template");
        fc.setInitialFileName("product_import_template.xlsx");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fc.showSaveDialog(productTable.getScene().getWindow());
        if (file == null) return;
        try {
            new ExcelService().writeImportTemplate(file.getAbsolutePath());
            AlertUtil.showInfo("Template Saved",
                "Import template saved to:\n" + file.getName() +
                "\n\nFill in your products and use '📥 Import Excel' to upload.");
        } catch (Exception e) {
            AlertUtil.showError("Error", "Could not save template: " + e.getMessage());
        }
    }

    @FXML
    private void importExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Products from Excel");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"));
        File file = fc.showOpenDialog(productTable.getScene().getWindow());
        if (file == null) return;

        ExcelService.ImportResult result = new ExcelService().importProducts(file.getAbsolutePath());

        if (result.hasErrors()) {
            String errorMsg = String.join("\n", result.getErrors().subList(
                0, Math.min(10, result.getErrors().size())));
            if (!AlertUtil.confirm("Import Warnings",
                    result.successCount() + " products ready to import.\n\nWarnings:\n" +
                    errorMsg + "\n\nContinue importing valid rows?")) return;
        }

        if (result.getProducts().isEmpty()) {
            AlertUtil.showWarning("No Data", "No valid products found in the file.");
            return;
        }

        int saved = 0;
        for (Product p : result.getProducts()) {
            if (productService.save(p) > 0) saved++;
        }
        AlertUtil.showInfo("Import Complete",
            saved + " of " + result.getProducts().size() + " products imported successfully.");
        refreshProducts();
    }

    @FXML
    private void exportExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Products to Excel");
        fc.setInitialFileName("products_" + java.time.LocalDate.now() + ".xlsx");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fc.showSaveDialog(productTable.getScene().getWindow());
        if (file == null) return;

        try {
            List<Product> toExport = filteredProducts != null
                ? new java.util.ArrayList<>(filteredProducts)
                : productService.getAllActive();
            new ExcelService().exportProducts(toExport, file.getAbsolutePath(), true);
            AlertUtil.showInfo("Exported",
                toExport.size() + " products exported to:\n" + file.getName());
        } catch (Exception e) {
            AlertUtil.showError("Export Failed", e.getMessage());
        }
    }
// ── Helpers ───────────────────────────────────────────────────────────────

    private int parseIntSafe(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private BigDecimal parseBigDecimalSafe(String text, BigDecimal fallback) {
        if (text == null || text.isBlank()) return fallback;
        try { return new BigDecimal(text.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private void showFormError(String msg) {
        formErrorLabel.setText(msg);
        formErrorLabel.setVisible(true);
        formErrorLabel.setManaged(true);
    }
    private void clearFormError() {
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);
    }
    private void setStatus(String msg) {
        // Update the product count label as a lightweight status
        productCountLabel.setText(msg);
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
