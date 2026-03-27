package com.minimartpos.controller.cashier;

import com.minimartpos.model.Category;
import com.minimartpos.model.Product;
import com.minimartpos.service.ProductService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.BarcodeUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller for the Quick Product creation dialog.
 *
 * Minimal required fields: barcode, name, selling price, category.
 * Optional: cost price, initial stock.
 *
 * On save:
 *  - Product is persisted to DB immediately
 *  - If "Add to cart" checkbox is selected, callback fires so
 *    POSTerminalController can add it to the current bill
 *
 * Usage:
 *   QuickProductController ctrl = loader.getController();
 *   ctrl.setBarcode("4890001234567");   // pre-fill from scanner
 *   ctrl.setOnSaved(product -> posController.addProductToCart(product, 1));
 */
public class QuickProductController implements Initializable {

    private static final Logger logger = LogManager.getLogger(QuickProductController.class);

    @FXML private TextField         fieldBarcode;
    @FXML private TextField         fieldName;
    @FXML private TextField         fieldPrice;
    @FXML private ComboBox<Category> fieldCategory;
    @FXML private TextField         fieldCost;
    @FXML private TextField         fieldStock;
    @FXML private CheckBox          addToCartCheck;
    @FXML private Label             errorLabel;
    @FXML private Button            saveBtn;

    private final ProductService productService = new ProductService();

    /** Called with the saved Product if "Add to cart" is checked. */
    private Consumer<Product> onSaved;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadCategories();
        // Tab order: barcode → name → price → category → stock → cost → save
        fieldBarcode.setOnAction(e -> fieldName.requestFocus());
        fieldName.setOnAction(e -> fieldPrice.requestFocus());
        fieldPrice.setOnAction(e -> fieldCategory.requestFocus());
    }

    // ── Pre-fill API ──────────────────────────────────────────────────────────

    /**
     * Pre-fills the barcode field — call when opening from barcode scanner.
     */
    public void setBarcode(String barcode) {
        if (barcode != null && !barcode.isBlank()) {
            fieldBarcode.setText(barcode.trim());
            fieldName.requestFocus();
        }
    }

    /**
     * Pre-fills the product name — call when opening from search "not found".
     */
    public void setProductName(String name) {
        if (name != null && !name.isBlank()) {
            fieldName.setText(name.trim());
        }
    }

    /**
     * Registers a callback invoked after the product is saved (and if add-to-cart
     * is checked). Receives the saved Product object.
     */
    public void setOnSaved(Consumer<Product> callback) {
        this.onSaved = callback;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadCategories() {
        List<Category> cats = productService.getAllCategories();
        fieldCategory.setItems(FXCollections.observableArrayList(cats));
        if (!cats.isEmpty()) fieldCategory.getSelectionModel().selectFirst();
    }

    @FXML
    private void generateBarcode() {
        fieldBarcode.setText(BarcodeUtil.generateEAN13());
    }

    @FXML
    private void addCategory() {
        String name = AlertUtil.promptText("New Category", "Category name:", "");
        if (name.isEmpty()) return;
        int id = productService.addCategory(name);
        if (id > 0) {
            Category newCat = new Category(id, name);
            fieldCategory.getItems().add(newCat);
            fieldCategory.setValue(newCat);
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @FXML
    private void saveProduct() {
        clearError();

        // ── Validate ──────────────────────────────────────────────────────────
        String barcode = fieldBarcode.getText().trim();
        String name    = fieldName.getText().trim();
        Category cat   = fieldCategory.getValue();

        if (ValidationUtil.isNullOrBlank(barcode)) {
            showError("Barcode is required. Click 'Generate' to auto-create one.");
            return;
        }
        if (ValidationUtil.isNullOrBlank(name)) {
            showError("Product name is required.");
            fieldName.requestFocus();
            return;
        }
        if (cat == null) {
            showError("Please select a category.");
            return;
        }

        BigDecimal price = CurrencyUtil.parse(fieldPrice.getText());
        if (!ValidationUtil.isPositive(price)) {
            showError("Selling price must be greater than 0.");
            fieldPrice.requestFocus();
            return;
        }

        // Optional fields with safe defaults
        BigDecimal cost  = CurrencyUtil.parse(fieldCost.getText());
        int stock;
        try {
            stock = fieldStock.getText().isBlank()
                ? 0 : Integer.parseInt(fieldStock.getText().trim());
        } catch (NumberFormatException e) {
            showError("Stock must be a whole number.");
            return;
        }

        // ── Build product ─────────────────────────────────────────────────────
        Product p = new Product();
        p.setBarcode(barcode);
        p.setName(name);
        p.setCategoryId(cat.getId());
        p.setCategoryName(cat.getName());
        p.setUnitPrice(price);
        p.setCostPrice(cost.compareTo(BigDecimal.ZERO) > 0 ? cost : BigDecimal.ZERO);
        p.setStockQuantity(stock);
        p.setReorderLevel(5);           // sensible default
        p.setTaxRate(BigDecimal.ZERO);  // no tax by default — admin can edit later
        p.setDiscountAllowed(true);
        p.setActive(true);

        // ── Save ──────────────────────────────────────────────────────────────
        int savedId = productService.save(p);
        if (savedId <= 0) {
            showError("Failed to save product. The barcode may already exist.");
            return;
        }
        p.setId(savedId);

        logger.info("Quick product created: id={} barcode={} name={}",
                    savedId, barcode, name);

        // ── Callback ──────────────────────────────────────────────────────────
        if (addToCartCheck.isSelected() && onSaved != null) {
            onSaved.accept(p);
        }

        close();
    }

    @FXML
    private void cancel() { close(); }

    private void close() {
        ((Stage) saveBtn.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
