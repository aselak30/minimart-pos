package com.minimartpos.controller.cashier;

import com.minimartpos.model.Bill;
import com.minimartpos.model.BillItem;
import com.minimartpos.model.Product;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.security.SessionManager;
import com.minimartpos.service.BillingService;
import com.minimartpos.service.ProductService;
import com.minimartpos.util.AlertUtil;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the POS Terminal screen.
 *
 * Responsibilities:
 * - Live clock / date display
 * - Product search (live filtering + barcode scan)
 * - Category pill filter bar
 * - Quick-product tile grid
 * - Cart TableView with inline qty editing
 * - Bill totals (subtotal / discount / tax / total / profit)
 * - Payment routing (cash, card, credit, mobile)
 * - New bill / void bill / discount dialog / return dialog
 * - Permission-gated UI elements
 * - Session inactivity tracking
 */
public class POSTerminalController implements Initializable {

    private static final Logger logger = LogManager.getLogger(POSTerminalController.class);

    // ── FXML: Top Bar ─────────────────────────────────────────────────────────
    @FXML
    private BorderPane rootPane;
    @FXML
    private Label cashierNameLabel;
    @FXML
    private Label shiftLabel;
    @FXML
    private Label clockLabel;
    @FXML
    private Label dateLabel;
    @FXML
    private Label billNumberLabel;
    @FXML
    private Label customerLabel;
    @FXML
    private Button customerBtn;
    @FXML
    private Button clearCustomerBtn;

    // ── FXML: Left Panel ──────────────────────────────────────────────────────
    @FXML
    private TextField searchField;
    @FXML
    private TextField barcodeField;
    @FXML
    private HBox categoryBar;
    @FXML
    private VBox quickGridPane;
    @FXML
    private Label quickGridHint;
    @FXML
    private VBox searchResultsPane;
    @FXML
    private FlowPane quickProductGrid;
    @FXML
    private ListView<Product> searchResultsList;
    @FXML
    private Label searchCountLabel;

    // ── FXML: Cart ────────────────────────────────────────────────────────────
    @FXML
    private TableView<BillItem> cartTable;
    @FXML
    private TableColumn<BillItem, String> colProduct;
    @FXML
    private TableColumn<BillItem, String> colQty;
    @FXML
    private TableColumn<BillItem, String> colPrice;
    @FXML
    private TableColumn<BillItem, String> colDisc;
    @FXML
    private TableColumn<BillItem, String> colTotal;
    @FXML
    private TableColumn<BillItem, String> colRemove;
    @FXML
    private Label itemCountLabel;

    // ── FXML: Totals ──────────────────────────────────────────────────────────
    @FXML
    private Label subtotalLabel;
    @FXML
    private TextField billDiscountField;
    @FXML
    private ToggleButton discToggle;
    @FXML
    private Label discountLabel;
    @FXML
    private Label taxLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private HBox profitRow;
    @FXML
    private Label profitLabel;

    // ── FXML: Buttons ─────────────────────────────────────────────────────────
    @FXML
    private Button creditBtn;
    @FXML
    private Button discountBtn;
    @FXML
    private Button voidBtn;
    @FXML
    private Button holdBtn;
    @FXML
    private Button retrieveBtn;
    @FXML
    private Button quickProductBtn;
    @FXML
    private Button adminBackBtn; // visible only when admin enters POS
    @FXML
    private Button reprintBtn; // reprint last completed bill

    // Tracks the last finalized bill for reprint
    private Bill lastCompletedBill = null;

    // ── FXML: Status Bar ──────────────────────────────────────────────────────
    @FXML
    private Label statusLabel;
    @FXML
    private Label dbStatusLabel;
    @FXML
    private Label machineLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private Bill activeBill;
    private final ObservableList<BillItem> cartItems = FXCollections.observableArrayList();
    private final BillingService billingService = new BillingService();
    private final ProductService productService = new ProductService();
    private Timeline clockTimeline;
    private String activeCategory = null; // null = All

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");
    private static final int QUICK_PRODUCT_LIMIT = 20;

    // ── Initialization ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTopBar();
        setupCartTable();
        setupSearchField();
        setupBarcodeField();
        setupCategoryBar();
        setupQuickProductGrid();
        setupPermissions();
        setupClock();
        setupOfflineMonitor();
        newBill(); // start with a fresh blank bill
        updateRetrieveButton();
        setStatus("Ready. Scan or search a product.");
        logger.info("POS Terminal initialized for user: {}",
                SessionManager.getCurrentUser().getUsername());

        Platform.runLater(this::setupKeyBindings);
    }

    private void setupKeyBindings() {
        if (rootPane != null && rootPane.getScene() != null) {
            // Auto focus on start
            barcodeField.requestFocus();

            // Re-focus anytime we return to the scene (like after a dialog)
            rootPane.getScene().windowProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    newV.focusedProperty().addListener((o, oldFocus, newFocus) -> {
                        if (newFocus)
                            Platform.runLater(() -> barcodeField.requestFocus());
                    });
                }
            });

            rootPane.getScene().setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case F1 -> {
                        searchField.requestFocus();
                        e.consume();
                    }
                    case F2 -> {
                        if (!cartTable.getSelectionModel().isEmpty()) {
                            BillItem selected = cartTable.getSelectionModel().getSelectedItem();
                            if (SessionManager.hasPermission(Permission.APPLY_LINE_ITEM_DISCOUNT)) {
                                String discStr = AlertUtil.promptText("Item Discount",
                                        "Enter percentage discount (e.g., 10):", "");
                                if (discStr != null && !discStr.trim().isEmpty()) {
                                    try {
                                        BigDecimal disc = new BigDecimal(discStr.trim());
                                        int idx = cartItems.indexOf(selected);
                                        BillingService.BillResult r = billingService.applyItemDiscount(activeBill, idx,
                                                disc, true);
                                        if (r.isSuccess())
                                            refreshCart();
                                        else
                                            AlertUtil.showWarning("Discount Error", r.getMessage());
                                    } catch (Exception ex) {
                                        AlertUtil.showWarning("Invalid", "Please enter a valid number.");
                                    }
                                }
                            }
                        }
                        e.consume();
                    }
                    case F3 -> {
                        openDiscountDialog();
                        e.consume();
                    }
                    case F4 -> {
                        openCustomerSearch();
                        e.consume();
                    }
                    case F5 -> {
                        payByCash();
                        e.consume();
                    }
                    case F6 -> {
                        reprintLastBill();
                        e.consume();
                    }
                    case F7 -> {
                        holdBill();
                        e.consume();
                    }
                    case F8 -> {
                        retrieveHeldBill();
                        e.consume();
                    }
                    case F9 -> {
                        if (!cartItems.isEmpty()) {
                            BillingService.BillResult r = billingService.removeItem(activeBill, cartItems.size() - 1);
                            if (r.isSuccess())
                                refreshCart();
                        }
                        e.consume();
                    }
                    case F10 -> {
                        clearCart();
                        e.consume();
                    }
                    case F11 -> {
                        java.util.List<String> keys = new java.util.ArrayList<>(
                                com.minimartpos.util.ThemeManager.THEMES.keySet());
                        int idx = keys.indexOf(com.minimartpos.util.ThemeManager.getUserTheme());
                        String nextTheme = keys.get((idx + 1) % keys.size());
                        com.minimartpos.util.ThemeManager.setUserTheme(nextTheme);
                        e.consume();
                    }
                    case F12 -> {
                        logout();
                        e.consume();
                    }
                    case DELETE -> {
                        if (cartTable.isFocused() && !cartTable.getSelectionModel().isEmpty()) {
                            int idx = cartTable.getSelectionModel().getSelectedIndex();
                            BillingService.BillResult r = billingService.removeItem(activeBill, idx);
                            if (r.isSuccess())
                                refreshCart();
                        }
                        e.consume();
                    }
                    case D -> {
                        if (e.isControlDown()) {
                            voidBill();
                            e.consume();
                        }
                    }
                    case P -> {
                        if (e.isControlDown()) {
                            reprintLastBill();
                            e.consume();
                        }
                    }
                    case C -> {
                        if (e.isControlDown()) {
                            new Thread(() -> com.minimartpos.hardware.CashDrawer.open()).start();
                            e.consume();
                        }
                    }
                    case H -> {
                        if (e.isControlDown()) {
                            AlertUtil.showInfo("Keyboard Shortcuts",
                                    "F1: Search Product\n" +
                                            "F2: Apply Discount to Selected Item\n" +
                                            "F3: Apply Bill Discount\n" +
                                            "F4: Open Customer Selection\n" +
                                            "F5: Pay via Cash\n" +
                                            "F6: Reprint Last Bill\n" +
                                            "F7: Hold Bill\n" +
                                            "F8: Retrieve Held Bill\n" +
                                            "F9: Remove Last Added Item\n" +
                                            "F10: Clear Cart\n" +
                                            "F11: Toggle Theme\n" +
                                            "F12: Logout\n" +
                                            "Ctrl+D: Delete Bill / Void\n" +
                                            "Ctrl+P: Reprint\n" +
                                            "Ctrl+C: Open Cash Drawer\n" +
                                            "DEL: Remove Selected Item in Cart");
                            e.consume();
                        }
                    }
                    default -> {
                    }
                }
            });
        }
    }

    // ── Setup Methods ─────────────────────────────────────────────────────────

    private void setupTopBar() {
        cashierNameLabel.setText(SessionManager.getCurrentUser().getFullName());
        shiftLabel.setText("Session started " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        machineLabel.setText("Machine: " +
                (SessionManager.getMachineId() != null ? SessionManager.getMachineId() : "1"));

        // Show "Back to Dashboard" button only for admins
        if (adminBackBtn != null) {
            boolean isAdmin = SessionManager.getCurrentUser().getRole() == com.minimartpos.model.enums.Role.ADMIN;
            adminBackBtn.setVisible(isAdmin);
            adminBackBtn.setManaged(isAdmin);
        }
        // Reprint disabled until first bill completed this session
        if (reprintBtn != null)
            reprintBtn.setDisable(true);
    }

    private void setupOfflineMonitor() {
        com.minimartpos.network.OfflineSync offlineSync = com.minimartpos.network.OfflineSync.getInstance();

        // Show warning immediately if already offline
        if (offlineSync.isOffline()) {
            setStatus("⚠ OFFLINE MODE — Bills will be saved locally and synced when connection is restored.");
            statusLabel.setStyle("-fx-background-color:-pos-warning; -fx-text-fill:white;");
        }

        // When DB goes offline mid-session
        offlineSync.setOnDisconnect(() -> {
            setStatus("⚠ OFFLINE MODE — Connection lost. Bills saved locally.");
            statusLabel.setStyle("-fx-background-color:-pos-warning; -fx-text-fill:white;");
        });

        // When DB reconnects and offline bills have been synced
        offlineSync.setOnReconnect(count -> {
            setStatus("✔ Reconnected. " + count + " offline bill" +
                    (count == 1 ? "" : "s") + " synced to database.");
            statusLabel.setStyle(""); // reset to default
        });
    }

    private void setupClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            LocalDateTime now = LocalDateTime.now();
            clockLabel.setText(now.format(TIME_FMT));
            dateLabel.setText(now.format(DATE_FMT));
            // Touch session on activity
            SessionManager.touch();
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();

        // Sync: when another machine changes stock, refresh our quick grid
        com.minimartpos.network.SyncManager.getInstance().addListener(
                com.minimartpos.network.SyncEvent.Type.STOCK_CHANGED,
                event -> Platform.runLater(() -> {
                    productService.invalidateCache();
                    // Only visually refresh if we're not mid-transaction
                    if (activeBill.getItems().isEmpty())
                        setupQuickProductGrid();
                    setStatus("ℹ Stock updated by another terminal.");
                }));
        com.minimartpos.network.SyncManager.getInstance().addListener(
                com.minimartpos.network.SyncEvent.Type.PRODUCT_UPDATED,
                event -> Platform.runLater(() -> {
                    productService.invalidateCache();
                    setStatus("ℹ Product catalogue updated by admin.");
                }));
    }

    private void setupCartTable() {
        cartTable.setItems(cartItems);
        cartTable.setEditable(true);

        // Product name column
        colProduct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));

        // Qty column — editable, inline text field
        colQty.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getQuantity())));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn());
        colQty.setOnEditCommit(event -> {
            BillItem item = event.getRowValue();
            int idx = cartItems.indexOf(item);
            try {
                BigDecimal newQty = new BigDecimal(event.getNewValue().trim());
                BillingService.BillResult result = billingService.updateQuantity(activeBill, idx, newQty);
                if (!result.isSuccess()) {
                    AlertUtil.showWarning("Cannot Update Quantity", result.getMessage());
                }
            } catch (NumberFormatException e) {
                AlertUtil.showWarning("Invalid Quantity", "Please enter a whole number.");
            }
            refreshCart();
        });

        // Price column — editable if user has permission
        colPrice.setCellValueFactory(
                c -> new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getUnitPrice())));
        if (SessionManager.hasPermission(Permission.CHANGE_SELLING_PRICE)) {
            colPrice.setCellFactory(TextFieldTableCell.forTableColumn());
            colPrice.setOnEditCommit(event -> {
                BillItem item = event.getRowValue();
                int idx = cartItems.indexOf(item);
                BigDecimal newPrice = CurrencyUtil.parse(event.getNewValue());
                BillingService.BillResult result = billingService.overrideItemPrice(activeBill, idx, newPrice);
                if (!result.isSuccess())
                    AlertUtil.showWarning("Price Override", result.getMessage());
                refreshCart();
            });
        }

        // Discount column — editable if user has permission
        colDisc.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDiscountPercent().compareTo(BigDecimal.ZERO) > 0
                        ? c.getValue().getDiscountPercent().toPlainString() + "%"
                        : "-"));
        if (SessionManager.hasPermission(Permission.APPLY_LINE_ITEM_DISCOUNT)) {
            colDisc.setCellFactory(TextFieldTableCell.forTableColumn());
            colDisc.setOnEditCommit(event -> {
                BillItem item = event.getRowValue();
                int idx = cartItems.indexOf(item);
                String raw = event.getNewValue().replace("%", "").trim();
                try {
                    BigDecimal pct = new BigDecimal(raw);
                    BillingService.BillResult r = billingService.applyItemDiscount(activeBill, idx, pct, true);
                    if (!r.isSuccess())
                        AlertUtil.showWarning("Discount", r.getMessage());
                } catch (NumberFormatException e) {
                    AlertUtil.showWarning("Invalid", "Enter a number like: 10");
                }
                refreshCart();
            });
        }

        // Total column
        colTotal.setCellValueFactory(
                c -> new SimpleStringProperty(CurrencyUtil.formatPlain(c.getValue().getLineTotal())));

        // Remove button column
        colRemove.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✖");
            {
                btn.setStyle("-fx-background-color:transparent; -fx-text-fill:-pos-danger; " +
                        "-fx-cursor:hand; -fx-font-size:13px;");
                btn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < cartItems.size()) {
                        BillingService.BillResult r = billingService.removeItem(activeBill, idx);
                        if (r.isSuccess())
                            refreshCart();
                        else
                            AlertUtil.showWarning("Cannot Remove", r.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic(empty ? null : btn);
            }
        });

        // Double-click row to edit qty via dialog
        cartTable.setRowFactory(tv -> {
            TableRow<BillItem> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openItemEditDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupSearchField() {
        searchField.textProperty().addListener((obs, old, text) -> {
            if (text == null || text.trim().isEmpty()) {
                showQuickGrid();
            } else {
                performSearch(text.trim());
            }
        });

        // ESC clears search
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                showQuickGrid();
            }
        });
    }

    private void setupBarcodeField() {
        // Barcode field auto-commits on Enter (from scanner) via onAction in FXML
        // Also support typing manually
        barcodeField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE)
                barcodeField.clear();
        });
    }

    private void setupCategoryBar() {
        // "All" button
        Button allBtn = makeCategoryPill("All", null);
        allBtn.getStyleClass().add("active");
        categoryBar.getChildren().add(allBtn);

        // Load categories in background
        new Thread(() -> {
            List<String> cats = productService.getActiveCategories();
            Platform.runLater(() -> {
                for (String cat : cats) {
                    categoryBar.getChildren().add(makeCategoryPill(cat, cat));
                }
            });
        }).start();
    }

    private Button makeCategoryPill(String label, String categoryId) {
        Button btn = new Button(label);
        btn.setStyle("-fx-background-radius:20; -fx-border-radius:20; " +
                "-fx-padding:4 14; -fx-cursor:hand; -fx-font-size:12px; " +
                "-fx-background-color:white; -fx-border-color:-pos-border; -fx-border-width:1.5;");
        btn.setOnAction(e -> {
            activeCategory = categoryId;
            // Reset all pill styles
            categoryBar.getChildren().forEach(n -> {
                if (n instanceof Button b) {
                    b.setStyle(b.getStyle().replace("-fx-background-color:-pos-primary;", "")
                            .replace("-fx-text-fill:white;", "")
                            + " -fx-background-color:white;");
                }
            });
            btn.setStyle(btn.getStyle().replace("-fx-background-color:white;", "")
                    + " -fx-background-color:-pos-primary; -fx-text-fill:white;");
            searchField.clear();
            setupQuickProductGrid();
        });
        return btn;
    }

    private void setupQuickProductGrid() {
        new Thread(() -> {
            List<Product> products = activeCategory == null
                    ? productService.getQuickProducts(QUICK_PRODUCT_LIMIT)
                    : productService.getByCategory(
                            productService.getAllActive().stream()
                                    .filter(p -> activeCategory.equals(p.getCategoryName()))
                                    .map(Product::getCategoryId)
                                    .findFirst().orElse(0));

            Platform.runLater(() -> {
                quickProductGrid.getChildren().clear();
                for (Product p : products) {
                    quickProductGrid.getChildren().add(makeProductTile(p));
                }
                if (products.isEmpty()) {
                    Label empty = new Label("No products found");
                    empty.getStyleClass().add("text-muted");
                    quickProductGrid.getChildren().add(empty);
                }
            });
        }).start();
    }

    private VBox makeProductTile(Product product) {
        VBox tile = new VBox(4);
        tile.setAlignment(Pos.CENTER);
        tile.setPrefSize(120, 88);
        tile.setMaxSize(130, 96);
        tile.getStyleClass().add("pos-product-tile");

        Label nameLabel = new Label(product.getName());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(110);
        nameLabel.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-alignment:center;");
        nameLabel.setAlignment(Pos.CENTER);

        Label priceLabel = new Label(CurrencyUtil.format(product.getUnitPrice()));
        priceLabel.setStyle("-fx-font-size:12px; -fx-text-fill:-pos-primary; -fx-font-weight:bold;");

        Label stockLabel = new Label("Stock: " + product.getStockQuantity());
        stockLabel.setStyle("-fx-font-size:10px; -fx-text-fill:" +
                (product.isLowStock() ? "-pos-warning;" : "-pos-text-secondary;"));

        tile.getChildren().addAll(nameLabel, priceLabel, stockLabel);

        // Out of stock overlay
        if (product.isOutOfStock()) {
            tile.setOpacity(0.45);
            tile.setDisable(true);
        } else {
            tile.setOnMouseClicked(e -> {
                if (product.isWeightBased()) {
                    addProductToCart(product, BigDecimal.ONE);
                } else {
                    BigDecimal qty = AlertUtil.promptBigDecimal(
                            "Quantity", "Enter quantity for " + product.getName() + ":", BigDecimal.ONE);
                    if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                        addProductToCart(product, qty);
                    }
                }
            });
        }

        // Low stock badge
        if (product.isLowStock() && !product.isOutOfStock()) {
            tile.setStyle(tile.getStyle() +
                    " -fx-border-color:-pos-warning; -fx-border-width:1.5;");
        }

        return tile;
    }

    private void setupPermissions() {
        // Show profit row only if permitted
        if (SessionManager.hasPermission(Permission.VIEW_BILL_PROFIT)) {
            profitRow.setVisible(true);
            profitRow.setManaged(true);
        }
        // Credit button only if customer management enabled
        if (!SessionManager.hasPermission(Permission.APPROVE_CREDIT)) {
            creditBtn.setDisable(true);
            creditBtn.setTooltip(new Tooltip("No permission to create credit bills"));
        }
        // Void button
        if (!SessionManager.hasPermission(Permission.VOID_BILL)) {
            voidBtn.setDisable(true);
        }
        // Discount button
        boolean canDiscount = SessionManager.hasAnyPermission(
                Permission.APPLY_BILL_DISCOUNT,
                Permission.APPLY_PERCENTAGE_DISCOUNT,
                Permission.APPLY_FIXED_DISCOUNT);
        discountBtn.setDisable(!canDiscount);

        // Quick product button — only shown to users with this permission
        if (SessionManager.hasPermission(Permission.CREATE_QUICK_PRODUCT)) {
            quickProductBtn.setVisible(true);
            quickProductBtn.setManaged(true);
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @FXML
    private void onSearchKeyReleased() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            showQuickGrid();
        } else {
            performSearch(text);
        }
        SessionManager.touch();
    }

    private void performSearch(String query) {
        new Thread(() -> {
            List<Product> results = productService.search(query);
            Platform.runLater(() -> {
                showSearchResults(results, query);
            });
        }).start();
    }

    private void showSearchResults(List<Product> results, String query) {
        quickGridPane.setVisible(false);
        quickGridPane.setManaged(false);
        searchResultsPane.setVisible(true);
        searchResultsPane.setManaged(true);
        searchCountLabel.setText(results.size() + " result" + (results.size() == 1 ? "" : "s"));

        searchResultsList.setItems(FXCollections.observableArrayList(results));
        searchResultsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    VBox info = new VBox(2);
                    Label name = new Label(p.getName());
                    name.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");
                    Label sub = new Label(p.getBarcode() + "  •  " + p.getCategoryName());
                    sub.setStyle("-fx-font-size:11px; -fx-text-fill:-pos-text-secondary;");
                    info.getChildren().addAll(name, sub);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    VBox priceBox = new VBox(1);
                    priceBox.setAlignment(Pos.CENTER_RIGHT);
                    Label price = new Label(CurrencyUtil.format(p.getUnitPrice()));
                    price.setStyle("-fx-font-weight:bold; -fx-text-fill:-pos-primary;");
                    Label stock = new Label("Qty: " + p.getStockQuantity());
                    stock.setStyle("-fx-font-size:11px; -fx-text-fill:" +
                            (p.isLowStock() ? "-pos-warning;" : "-pos-text-secondary;"));
                    priceBox.getChildren().addAll(price, stock);
                    row.getChildren().addAll(info, spacer, priceBox);
                    setGraphic(row);

                    if (p.isOutOfStock()) {
                        setOpacity(0.5);
                        setDisable(true);
                    }
                }
            }
        });

        searchResultsList.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 1) {
                Product selected = searchResultsList.getSelectionModel().getSelectedItem();
                if (selected != null && !selected.isOutOfStock()) {
                    if (selected.isWeightBased()) {
                        addProductToCart(selected, BigDecimal.ONE);
                        searchField.clear();
                        showQuickGrid();
                    } else {
                        BigDecimal qty = AlertUtil.promptBigDecimal(
                                "Quantity", "Enter quantity for " + selected.getName() + ":", BigDecimal.ONE);
                        if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                            addProductToCart(selected, qty);
                            searchField.clear();
                            showQuickGrid();
                        }
                    }
                }
            }
        });

        // Show "Create New Product" placeholder when no results found
        if (results.isEmpty() &&
                SessionManager.hasPermission(Permission.CREATE_QUICK_PRODUCT)) {
            javafx.scene.layout.VBox placeholder = new javafx.scene.layout.VBox(10);
            placeholder.setAlignment(Pos.CENTER);
            placeholder.setStyle("-fx-padding:30;");
            Label msg = new Label("No products found for \"" + query + "\"");
            msg.setStyle("-fx-text-fill:-pos-text-secondary; -fx-font-size:13px;");
            javafx.scene.control.Button createBtn = new javafx.scene.control.Button(
                    "⚡ Create \"" + (query.length() > 30 ? query.substring(0, 30) + "…" : query) + "\" as new product");
            createBtn.setStyle(
                    "-fx-background-color:-pos-primary; -fx-text-fill:white; " +
                            "-fx-font-size:13px; -fx-padding:8 16; -fx-background-radius:6; -fx-cursor:hand;");
            createBtn.setOnAction(e -> openQuickProductDialog(null, query));
            placeholder.getChildren().addAll(msg, createBtn);
            searchResultsList.setPlaceholder(placeholder);
        } else if (results.isEmpty()) {
            searchResultsList.setPlaceholder(
                    new Label("No products found for \"" + query + "\""));
        }
    }

    private void showQuickGrid() {
        searchResultsPane.setVisible(false);
        searchResultsPane.setManaged(false);
        quickGridPane.setVisible(true);
        quickGridPane.setManaged(true);
    }

    // ── Barcode Scanner ───────────────────────────────────────────────────────

    @FXML
    private void onBarcodeEntered() {
        String barcode = barcodeField.getText().trim();
        if (barcode.isEmpty())
            return;

        new Thread(() -> {
            var product = productService.findByBarcode(barcode);
            Platform.runLater(() -> {
                if (product.isPresent()) {
                    addProductToCart(product.get(), BigDecimal.ONE);
                    setStatus("Added: " + product.get().getName());
                } else {
                    setStatus("⚠ Product not found for barcode: " + barcode);
                    // Offer quick create if cashier has permission
                    if (SessionManager.hasPermission(Permission.CREATE_QUICK_PRODUCT)) {
                        if (AlertUtil.confirm("Product Not Found",
                                "Barcode '" + barcode
                                        + "' is not in the system.\n\nCreate a new product with this barcode?")) {
                            openQuickProductDialog(barcode, null);
                        }
                    } else {
                        AlertUtil.showWarning("Not Found",
                                "No product found for barcode: " + barcode);
                    }
                }
                barcodeField.clear();
                barcodeField.requestFocus();
            });
        }).start();

        SessionManager.touch();
    }

    // ── Cart Operations ───────────────────────────────────────────────────────

    private void addProductToCart(Product product, BigDecimal quantity) {
        // Handle weight-based products automatically!
        if (product.isWeightBased()) {
            quantity = promptForWeight(product);
            if (quantity == null) {
                // User cancelled weight input
                return;
            }
        }

        BillingService.BillResult result = billingService.addProduct(activeBill, product, quantity);
        if (result.isSuccess()) {
            refreshCart();
            setStatus("✔ " + result.getMessage());
        } else {
            setStatus("⚠ " + result.getMessage());
            AlertUtil.showWarning("Cannot Add Product", result.getMessage());
        }
        SessionManager.touch();
        Platform.runLater(() -> barcodeField.requestFocus()); // Return focus after add
    }

    private BigDecimal promptForWeight(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/cashier/WeightInput.fxml"));
            Parent root = loader.load();
            WeightInputController ctrl = loader.getController();
            ctrl.setProduct(product);

            Stage stage = new Stage();
            stage.setTitle("Enter Weight");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(SceneManager.getPrimaryStage());

            Scene scene = new Scene(root);
            com.minimartpos.util.ThemeManager.applyCurrentUserTheme(scene);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();

            if (ctrl.isConfirmed()) {
                BigDecimal weight = ctrl.getWeightValue();
                if (weight != null && product.getMinWeight() != null && product.getMaxWeight() != null) {
                    if (weight.compareTo(product.getMinWeight()) < 0 || weight.compareTo(product.getMaxWeight()) > 0) {
                        AlertUtil.showWarning("Invalid Weight", "Weight must be between " +
                                product.getMinWeight() + " and " + product.getMaxWeight() + " "
                                + product.getWeightUnit());
                        return null; // Force them to try again if invalid
                    }
                }
                BillItem.class.getMethod("setWeight", BigDecimal.class); // Check if we should use weight? In
                                                                         // BillingService addProduct handles it via
                                                                         // unit_price logic. The quantity IS the
                                                                         // weight.
                return weight;
            }
        } catch (Exception e) {
            logger.error("Failed to open weight input dialog", e);
            AlertUtil.showError("Error", "Could not open weight input dialog: " + e.getMessage());
        }
        return null; // Cancelled
    }

    private void openItemEditDialog(BillItem item) {
        int idx = cartItems.indexOf(item);
        BigDecimal qty = AlertUtil.promptBigDecimal(
                "Edit Item",
                "Quantity for: " + item.getProductName(),
                item.getQuantity());
        if (qty.compareTo(BigDecimal.ZERO) >= 0) {
            BillingService.BillResult r = billingService.updateQuantity(activeBill, idx, qty);
            if (!r.isSuccess())
                AlertUtil.showWarning("Update Failed", r.getMessage());
            refreshCart();
        }
    }

    @FXML
    private void clearCart() {
        if (activeBill.getItems().isEmpty())
            return;
        if (AlertUtil.confirm("Clear Cart", "Remove all items from the current bill?")) {
            activeBill.getItems().clear();
            activeBill.recalculate();
            refreshCart();
            setStatus("Cart cleared.");
        }
    }

    // ── Bill Discount ─────────────────────────────────────────────────────────

    @FXML
    private void applyBillDiscount() {
        String text = billDiscountField.getText().trim();
        if (text.isEmpty()) {
            activeBill.setDiscountAmount(BigDecimal.ZERO);
            activeBill.recalculate();
            refreshTotals();
            return;
        }
        BigDecimal value = CurrencyUtil.parse(text);
        boolean isPct = discToggle.isSelected();
        BillingService.BillResult r = billingService.applyBillDiscount(activeBill, value, isPct);
        if (!r.isSuccess()) {
            AlertUtil.showWarning("Discount Error", r.getMessage());
            billDiscountField.clear();
        }
        refreshTotals();
    }

    @FXML
    private void openDiscountDialog() {
        // A quick dialog: enter % or amount
        ChoiceDialog<String> typeChoice = new ChoiceDialog<>("Percentage", "Percentage", "Fixed Amount");
        typeChoice.setTitle("Apply Bill Discount");
        typeChoice.setHeaderText(null);
        typeChoice.setContentText("Discount type:");
        String type = typeChoice.showAndWait().orElse(null);
        if (type == null)
            return;

        boolean isPct = type.equals("Percentage");
        double val = AlertUtil.promptNumber("Bill Discount",
                isPct ? "Enter discount (%): " : "Enter discount amount (Rs): ", 0);
        if (val < 0)
            return;

        BillingService.BillResult r = billingService.applyBillDiscount(
                activeBill, BigDecimal.valueOf(val), isPct);
        if (r.isSuccess()) {
            billDiscountField.setText(String.valueOf(val));
            discToggle.setSelected(isPct);
            refreshTotals();
        } else {
            AlertUtil.showWarning("Discount Error", r.getMessage());
        }
    }

    // ── Payment Handlers ──────────────────────────────────────────────────────

    @FXML
    private void payByCash() {
        if (!validateBillForPayment())
            return;
        openPaymentDialog(Bill.PayType.CASH);
    }



    @FXML
    private void payByCredit() {
        if (!validateBillForPayment())
            return;
        if (activeBill.getCustomerId() <= 0) {
            AlertUtil.showWarning("Credit Payment",
                    "Please select a customer before creating a credit bill.");
            return;
        }
        openPaymentDialog(Bill.PayType.CREDIT);
    }



    private boolean validateBillForPayment() {
        if (activeBill.getItems().isEmpty()) {
            AlertUtil.showWarning("Empty Bill", "Please add items to the bill before payment.");
            return false;
        }
        return true;
    }

    private void openPaymentDialog(Bill.PayType payType) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/cashier/Payment.fxml"));
            Parent root = loader.load();

            PaymentController controller = loader.getController();
            controller.setup(activeBill, payType, this::onPaymentConfirmed);

            Stage stage = new Stage();
            stage.setTitle(payType.name().charAt(0) + payType.name().substring(1).toLowerCase()
                    + " Payment");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(SceneManager.getPrimaryStage());
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

        } catch (Exception e) {
            logger.error("Failed to open payment dialog", e);
            AlertUtil.showError("Error", "Could not open payment dialog: " + e.getMessage());
        }
    }

    /**
     * Called by PaymentController when payment is confirmed.
     */
    public void onPaymentConfirmed(BigDecimal paidAmount, Bill.PayType payType) {
        BillingService.BillResult result = billingService.finalizeBill(activeBill, paidAmount, payType);
        if (result.isSuccess()) {
            String billNum = result.getMessage();
            setStatus("✔ Bill " + billNum + " completed! Change: " +
                    CurrencyUtil.format(activeBill.getChangeAmount()));

            // Open cash drawer for cash payments
            if (payType == Bill.PayType.CASH) {
                new Thread(() -> com.minimartpos.hardware.CashDrawer.openAfterSale(billNum)).start();
            }

            // ── Cash limit warning ────────────────────────────────────────────
            com.minimartpos.model.User me = SessionManager.getCurrentUser();
            if (me.hasCashLimit()) {
                try {
                    BigDecimal todayCash = billingService.getTodayCashCollected(me.getId());
                    BigDecimal limit = me.getCashLimit();
                    BigDecimal pct = todayCash.multiply(new BigDecimal("100"))
                            .divide(limit, 0, java.math.RoundingMode.HALF_UP);
                    if (todayCash.compareTo(limit) >= 0) {
                        AlertUtil.showWarning("⚠ Cash Limit Reached",
                                "Your cash collection (" + CurrencyUtil.format(todayCash) +
                                        ") has reached your limit of " + CurrencyUtil.format(limit) +
                                        ".\nPlease deposit cash with your supervisor.");
                    } else if (pct.intValue() >= 80) {
                        setStatus("⚠ Cash limit " + pct + "% reached — " +
                                CurrencyUtil.format(todayCash) + " / " + CurrencyUtil.format(limit));
                    }
                } catch (Exception e) {
                    logger.warn("Could not check cash limit: {}", e.getMessage());
                }
            }

            // Show receipt preview → user chooses Print or Skip
            Bill printBill = activeBill;
            lastCompletedBill = activeBill; // save for reprint
            newBill();
            if (reprintBtn != null)
                reprintBtn.setDisable(false);
            showReceiptPreview(printBill);

        } else {
            AlertUtil.showError("Payment Failed", result.getMessage());
        }
    }

    /**
     * Opens the receipt preview dialog for the given bill.
     * Called automatically after payment, and manually via Reprint button.
     */
    private void showReceiptPreview(Bill bill) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/cashier/ReceiptPreview.fxml"));
            Parent root = loader.load();
            ReceiptPreviewController ctrl = loader.getController();
            ctrl.setBill(bill);

            Stage stage = new Stage();
            stage.setTitle("Receipt — Bill #" + bill.getBillNumber());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(SceneManager.getPrimaryStage());
            stage.setResizable(false);

            Scene scene = new Scene(root);
            // Apply current theme
            com.minimartpos.util.ThemeManager.applyCurrentUserTheme(scene);
            // Also apply main stylesheet
            URL css = getClass().getResource("/css/main.css");
            if (css != null)
                scene.getStylesheets().add(0, css.toExternalForm());

            stage.setScene(scene);
            stage.show(); // non-blocking — cashier can start next bill while dialog open
        } catch (Exception e) {
            logger.error("Could not open receipt preview: {}", e.getMessage(), e);
            // Fallback: print silently
            new Thread(() -> {
                try {
                    new com.minimartpos.hardware.PrinterManager().printReceipt(bill);
                } catch (Exception ex) {
                    logger.error("Fallback print failed: {}", ex.getMessage());
                }
            }, "print-fallback").start();
        }
    }

    // ── Bill Actions ──────────────────────────────────────────────────────────

    @FXML
    public void newBill() {
        activeBill = billingService.createNewBill();
        cartItems.clear();
        billNumberLabel.setText(activeBill.getBillNumber());
        billDiscountField.clear();
        clearCustomer();
        refreshTotals();
        updateRetrieveButton();
        setStatus("New bill started.");
        searchField.clear();
        productService.invalidateCache();
        setupQuickProductGrid();
        showQuickGrid();
        logger.debug("New bill created: {}", activeBill.getBillNumber());
    }

    @FXML
    private void voidBill() {
        if (!SessionManager.hasPermission(Permission.VOID_BILL)) {
            AlertUtil.showWarning("Permission Denied", "You do not have permission to void bills.");
            return;
        }
        if (activeBill.getStatus() == Bill.Status.DRAFT && activeBill.getItems().isEmpty()) {
            AlertUtil.showInfo("Void Bill", "The bill is already empty.");
            return;
        }
        String reason = AlertUtil.promptText("Void Bill", "Reason for voiding:", "");
        if (reason.isEmpty())
            return;

        if (activeBill.getStatus() == Bill.Status.FINALIZED) {
            BillingService.BillResult r = billingService.voidBill(activeBill, reason);
            if (r.isSuccess()) {
                AlertUtil.showInfo("Voided", "Bill has been voided.");
                newBill();
            } else {
                AlertUtil.showError("Void Failed", r.getMessage());
            }
        } else {
            // Draft bill — just clear it
            activeBill.getItems().clear();
            activeBill.recalculate();
            refreshCart();
            setStatus("Bill cleared.");
        }
    }

    @FXML
    public void holdBill() {
        if (activeBill.getItems().isEmpty()) {
            AlertUtil.showWarning("Hold Bill", "The cart is empty — nothing to hold.");
            return;
        }
        String label = AlertUtil.promptText(
                "Hold Bill",
                "Enter a label for this held bill (e.g. customer name):",
                "Customer " + (billingService.getHeldBills().size() + 1));
        if (label == null)
            return; // cancelled

        BillingService.BillResult result = billingService.holdBill(activeBill, label);
        if (result.isSuccess()) {
            setStatus("⏸ Bill held: \"" + result.getMessage() + "\"");
            updateRetrieveButton();
            newBill();
        } else {
            AlertUtil.showWarning("Cannot Hold", result.getMessage());
        }
    }

    @FXML
    public void retrieveHeldBill() {
        java.util.Map<String, Bill> held = billingService.getHeldBills();
        if (held.isEmpty()) {
            AlertUtil.showInfo("No Held Bills", "There are no bills currently on hold.");
            return;
        }

        // If only one held bill, retrieve it directly
        if (held.size() == 1) {
            String onlyLabel = held.keySet().iterator().next();
            doRetrieve(onlyLabel);
            return;
        }

        // Multiple held bills — show a chooser dialog
        java.util.List<String> labels = new java.util.ArrayList<>(held.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
        dialog.setTitle("Retrieve Held Bill");
        dialog.setHeaderText(null);
        dialog.setContentText("Select held bill to retrieve:");
        dialog.showAndWait().ifPresent(this::doRetrieve);
    }

    private void doRetrieve(String label) {
        // If current bill has items, offer to hold it first
        if (!activeBill.getItems().isEmpty()) {
            if (!AlertUtil.confirm("Replace Current Bill",
                    "The current bill has " + activeBill.getItemCount() +
                            " item(s). Hold current bill and retrieve \"" + label + "\"?")) {
                return;
            }
            billingService.holdBill(activeBill,
                    "Auto-hold " + activeBill.getBillNumber());
        }

        billingService.retrieveHeldBill(label).ifPresent(bill -> {
            activeBill = bill;
            refreshCart();
            billNumberLabel.setText(bill.getBillNumber());
            setStatus("📂 Retrieved: \"" + label + "\"");
            updateRetrieveButton();
        });
    }

    private void updateRetrieveButton() {
        int count = billingService.getHeldBills().size();
        if (retrieveBtn != null) {
            retrieveBtn.setText("📂 Retrieve" + (count > 0 ? " (" + count + ")" : ""));
            retrieveBtn.setStyle(count > 0
                    ? "-fx-background-color:-pos-accent; -fx-text-fill:white; " +
                            "-fx-font-weight:bold; -fx-background-radius:6; -fx-cursor:hand;"
                    : "");
        }
    }

    @FXML
    private void openReturnDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/cashier/Return.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Return / Refund");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(SceneManager.getPrimaryStage());
            stage.setScene(new javafx.scene.Scene(root));
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to open return dialog", e);
            AlertUtil.showError("Error", "Could not open return dialog: " + e.getMessage());
        }
    }

    // ── Quick Product ─────────────────────────────────────────────────────────

    /** Called by the ⚡ Quick Product topbar button. */
    @FXML
    private void openQuickProduct() {
        openQuickProductDialog(null, null);
    }

    /**
     * Opens the Quick Product dialog.
     *
     * @param prefilledBarcode Barcode to pre-fill (from scanner); null = blank
     * @param prefilledName    Name to pre-fill (from search query); null = blank
     */
    public void openQuickProductDialog(String prefilledBarcode, String prefilledName) {
        if (!SessionManager.hasPermission(Permission.CREATE_QUICK_PRODUCT)) {
            AlertUtil.showWarning("Permission Denied",
                    "You do not have permission to create products during billing.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/cashier/QuickProduct.fxml"));
            javafx.scene.Parent root = loader.load();

            com.minimartpos.controller.cashier.QuickProductController ctrl = loader.getController();
            if (prefilledBarcode != null)
                ctrl.setBarcode(prefilledBarcode);
            if (prefilledName != null)
                ctrl.setProductName(prefilledName);

            // Callback: add new product to cart after save
            ctrl.setOnSaved(product -> {
                productService.invalidateCache();
                addProductToCart(product, java.math.BigDecimal.ONE);
                setupQuickProductGrid(); // refresh grid to show new product
            });

            Stage stage = new Stage();
            stage.setTitle("Quick Add Product");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(SceneManager.getPrimaryStage());
            stage.setScene(new javafx.scene.Scene(root));
            stage.setResizable(false);
            stage.showAndWait();

        } catch (Exception e) {
            logger.error("Failed to open quick product dialog", e);
            AlertUtil.showError("Error", "Could not open quick product dialog.");
        }
    }

    @FXML
    private void openCustomerSearch() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/cashier/CustomerSearch.fxml"));
            javafx.scene.Parent root = loader.load();
            CustomerSearchController ctrl = loader.getController();
            ctrl.setOnSelect((id, name) -> {
                if (id == 0)
                    clearCustomer();
                else
                    setCustomer(id, name);
            });
            Stage stage = new Stage();
            stage.setTitle("Select Customer");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(SceneManager.getPrimaryStage());
            stage.setScene(new javafx.scene.Scene(root));
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to open customer search", e);
            AlertUtil.showError("Error", "Could not open customer search: " + e.getMessage());
        }
    }

    @FXML
    public void clearCustomer() {
        activeBill.setCustomerId(0);
        activeBill.setCustomerName(null);
        customerLabel.setText("Walk-in");
        clearCustomerBtn.setVisible(false);
        clearCustomerBtn.setManaged(false);
    }

    public void setCustomer(int id, String name) {
        activeBill.setCustomerId(id);
        activeBill.setCustomerName(name);
        customerLabel.setText(name);
        clearCustomerBtn.setVisible(true);
        clearCustomerBtn.setManaged(true);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML
    private void endShift() {
        if (!activeBill.getItems().isEmpty()) {
            if (!AlertUtil.confirm("End Shift", "You have items in the cart. End shift anyway?"))
                return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/cashier/Shift.fxml"));
            javafx.scene.Parent root = loader.load();
            ShiftController ctrl = loader.getController();
            ctrl.setOnShiftClosed(() -> {
                if (clockTimeline != null)
                    clockTimeline.stop();
                SessionManager.logout();
                SceneManager.clearStack();
                SceneManager.navigateTo("shared/Login.fxml");
            });
            Stage stage = new Stage();
            stage.setTitle("End Shift");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(SceneManager.getPrimaryStage());
            stage.setScene(new javafx.scene.Scene(root));
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to open shift dialog", e);
            AlertUtil.showError("Error", "Could not open shift dialog: " + e.getMessage());
        }
    }

    @FXML
    private void logout() {
        if (!activeBill.getItems().isEmpty()) {
            if (!AlertUtil.confirm("Logout",
                    "You have items in the cart. Logout anyway? The current bill will be lost.")) {
                return;
            }
        }
        if (!billingService.getHeldBills().isEmpty()) {
            if (!AlertUtil.confirm("Held Bills",
                    billingService.getHeldBills().size() +
                            " bill(s) are on hold and will be lost. Logout anyway?")) {
                return;
            }
        }
        billingService.clearHeldBills();
        if (clockTimeline != null)
            clockTimeline.stop();
        SessionManager.logout();
        SceneManager.clearStack();
        SceneManager.navigateTo("shared/Login.fxml");
    }

    @FXML
    private void openSettings() {
        // Cashier settings: only accessible if admin permission granted
        if (SessionManager.hasPermission(com.minimartpos.model.enums.Permission.ACCESS_SYSTEM_SETTINGS)) {
            SceneManager.navigateTo("admin/Settings.fxml");
        } else {
            AlertUtil.showWarning("Access Denied",
                    "You do not have permission to access system settings.");
        }
    }

    // ── Refresh Helpers ───────────────────────────────────────────────────────

    private void refreshCart() {
        cartItems.setAll(activeBill.getItems());
        refreshTotals();
        billNumberLabel.setText(activeBill.getBillNumber());
        int totalItems = activeBill.getItemCount();
        itemCountLabel.setText(totalItems + " item" + (totalItems == 1 ? "" : "s"));
        // Refresh quick grid stock counts
        setupQuickProductGrid();
    }

    private void refreshTotals() {
        activeBill.recalculate();
        subtotalLabel.setText(CurrencyUtil.formatPlain(activeBill.getSubtotal()));
        discountLabel.setText("- " + CurrencyUtil.formatPlain(activeBill.getDiscountAmount()));
        taxLabel.setText(CurrencyUtil.formatPlain(activeBill.getTaxAmount()));
        totalLabel.setText(CurrencyUtil.format(activeBill.getTotalAmount()));

        if (SessionManager.hasPermission(Permission.VIEW_BILL_PROFIT)) {
            profitLabel.setText(CurrencyUtil.formatPlain(activeBill.getProfitTotal()));
        }
    }

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /**
     * Reprints the last completed bill receipt.
     * Button is disabled until the first bill is completed this session.
     */
    @FXML
    private void reprintLastBill() {
        if (lastCompletedBill == null) {
            AlertUtil.showWarning("No Bill", "No completed bill to reprint in this session.");
            return;
        }
        showReceiptPreview(lastCompletedBill);
    }

    /**
     * Admin-only: returns to the admin dashboard from the POS terminal.
     * Bound to the "← Dashboard" button shown only when an admin is logged in.
     */
    @FXML
    private void adminBackToDashboard() {
        if (SessionManager.getCurrentUser().getRole() != com.minimartpos.model.enums.Role.ADMIN)
            return;

        if (!activeBill.getItems().isEmpty()) {
            if (!AlertUtil.confirm("Leave POS",
                    "You have items in the cart. Go back to Dashboard? The current bill will be discarded.")) {
                return;
            }
        }
        if (clockTimeline != null)
            clockTimeline.stop();
        SceneManager.navigateTo("admin/AdminDashboard.fxml");
    }
}
