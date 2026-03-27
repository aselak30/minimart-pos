package com.minimartpos.service;

import com.minimartpos.model.Bill;
import com.minimartpos.model.BillItem;
import com.minimartpos.model.Product;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.repository.BillRepository;
import com.minimartpos.repository.ProductRepository;
import com.minimartpos.security.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Core billing business logic for the POS terminal.
 *
 * Responsibilities:
 *  - Create / manage the active Bill in-memory
 *  - Add / remove / update cart items
 *  - Apply discounts (item-level and bill-level)
 *  - Override prices (with permission check)
 *  - Finalize bill (persist, deduct stock, generate bill number)
 *  - Void bills
 */
public class BillingService {

    private static final Logger logger = LogManager.getLogger(BillingService.class);

    private final BillRepository    billRepo    = new BillRepository();
    private final ProductRepository productRepo = new ProductRepository();
    private final StockService      stockService = new StockService();

    // ── Bill Lifecycle ────────────────────────────────────────────────────────

    /**
     * Creates a fresh empty bill for the current cashier session.
     */
    public Bill createNewBill() {
        Bill bill = new Bill();
        bill.setCashierId(SessionManager.getCurrentUser().getId());
        bill.setCashierName(SessionManager.getCurrentUser().getFullName());
        bill.setBillNumber(generateTempBillNumber());
        logger.debug("New bill created: {}", bill.getBillNumber());
        return bill;
    }

    /**
     * Generates a temporary bill number (replaced with DB-sequence on finalize).
     */
    private String generateTempBillNumber() {
        return "DRAFT-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HHmmss"));
    }

    /**
     * Generates the final persisted bill number: BILL-YYYYMMDD-NNNN.
     */
    public String generateFinalBillNumber() {
        String date   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int    seqNum = billRepo.getNextSequenceForDate(date);
        return String.format("BILL-%s-%04d", date, seqNum);
    }

    // ── Cart Operations ───────────────────────────────────────────────────────

    /**
     * Adds a product to the bill. If the product already exists in the cart,
     * increments quantity instead.
     *
     * @return  Result with success/failure and message.
     */
    public BillResult addProduct(Bill bill, Product product, int quantity) {
        if (product == null) {
            return BillResult.fail("Product not found.");
        }
        if (!product.isActive()) {
            return BillResult.fail("Product '" + product.getName() + "' is inactive.");
        }
        if (product.isOutOfStock() && quantity > 0) {
            return BillResult.fail("'" + product.getName() + "' is out of stock.");
        }
        if (product.getStockQuantity() < quantity) {
            return BillResult.fail("Insufficient stock. Available: " + product.getStockQuantity());
        }

        // Check if product already in cart
        Optional<BillItem> existing = bill.getItems().stream()
                .filter(i -> i.getProductId() == product.getId())
                .findFirst();

        if (existing.isPresent()) {
            BillItem item    = existing.get();
            int      newQty  = item.getQuantity() + quantity;
            if (newQty > product.getStockQuantity()) {
                return BillResult.fail("Cannot add more. Available: " + product.getStockQuantity());
            }
            item.setQuantity(newQty);
        } else {
            BillItem item = new BillItem(product, quantity);
            bill.getItems().add(item);
        }

        bill.recalculate();
        logger.debug("Added {} x '{}' to bill {}", quantity, product.getName(), bill.getBillNumber());
        return BillResult.ok("Added: " + product.getName());
    }

    /**
     * Removes a line item from the cart by index.
     */
    public BillResult removeItem(Bill bill, int itemIndex) {
        if (itemIndex < 0 || itemIndex >= bill.getItems().size()) {
            return BillResult.fail("Invalid item index.");
        }
        BillItem removed = bill.getItems().remove(itemIndex);
        bill.recalculate();
        logger.debug("Removed '{}' from bill {}", removed.getProductName(), bill.getBillNumber());
        return BillResult.ok("Removed: " + removed.getProductName());
    }

    /**
     * Updates the quantity of a cart item.
     * Passing quantity = 0 removes the item.
     */
    public BillResult updateQuantity(Bill bill, int itemIndex, int newQty) {
        if (itemIndex < 0 || itemIndex >= bill.getItems().size()) {
            return BillResult.fail("Invalid item index.");
        }
        if (newQty == 0) {
            return removeItem(bill, itemIndex);
        }
        if (newQty < 0 && !SessionManager.hasPermission(Permission.ADD_NEGATIVE_QUANTITY)) {
            return BillResult.fail("You do not have permission to add negative quantities.");
        }

        BillItem item    = bill.getItems().get(itemIndex);
        Optional<Product> prod = productRepo.findById(item.getProductId());
        if (prod.isPresent() && newQty > prod.get().getStockQuantity()) {
            return BillResult.fail("Insufficient stock. Available: " + prod.get().getStockQuantity());
        }

        item.setQuantity(newQty);
        bill.recalculate();
        return BillResult.ok("Quantity updated.");
    }

    // ── Pricing & Discounts ───────────────────────────────────────────────────

    /**
     * Overrides the selling price of a cart item.
     * Requires CHANGE_SELLING_PRICE permission.
     */
    public BillResult overrideItemPrice(Bill bill, int itemIndex, BigDecimal newPrice) {
        if (!SessionManager.hasPermission(Permission.CHANGE_SELLING_PRICE)) {
            return BillResult.fail("You do not have permission to change selling price.");
        }
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) < 0) {
            return BillResult.fail("Invalid price.");
        }
        if (newPrice.compareTo(BigDecimal.ZERO) == 0
                && !SessionManager.hasPermission(Permission.ADD_FREE_ITEM)) {
            return BillResult.fail("You do not have permission to add free items.");
        }

        BillItem item = bill.getItems().get(itemIndex);
        item.setUnitPrice(newPrice);
        item.setPriceOverride(true);
        bill.recalculate();
        logger.info("Price override on '{}' by user {} — new price: {}",
                    item.getProductName(),
                    SessionManager.getCurrentUser().getUsername(),
                    newPrice);
        return BillResult.ok("Price updated.");
    }

    /**
     * Applies a percentage or fixed discount to a single cart item.
     */
    public BillResult applyItemDiscount(Bill bill, int itemIndex,
                                        BigDecimal discountValue, boolean isPercent) {
        if (!SessionManager.hasPermission(Permission.APPLY_LINE_ITEM_DISCOUNT)) {
            return BillResult.fail("You do not have permission to apply line discounts.");
        }

        BillItem item    = bill.getItems().get(itemIndex);
        Product  product = productRepo.findById(item.getProductId()).orElse(null);

        // Check max discount limit
        if (product != null && isPercent && product.getMaxDiscountPercent() != null) {
            if (discountValue.compareTo(product.getMaxDiscountPercent()) > 0
                    && !SessionManager.hasPermission(Permission.OVERRIDE_DISCOUNT_LIMIT)) {
                return BillResult.fail("Discount exceeds the maximum allowed ("
                                       + product.getMaxDiscountPercent() + "%) for this product.");
            }
        }

        if (isPercent) {
            if (discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                return BillResult.fail("Discount cannot exceed 100%.");
            }
            item.setDiscountPercent(discountValue);
            item.setDiscountAmount(BigDecimal.ZERO);
        } else {
            item.setDiscountAmount(discountValue);
            item.setDiscountPercent(BigDecimal.ZERO);
        }

        bill.recalculate();
        return BillResult.ok("Discount applied.");
    }

    /**
     * Applies a bill-level discount (percentage or fixed amount).
     * Requires APPLY_BILL_DISCOUNT permission.
     */
    public BillResult applyBillDiscount(Bill bill, BigDecimal discountValue, boolean isPercent) {
        if (!SessionManager.hasPermission(Permission.APPLY_BILL_DISCOUNT)
                && !SessionManager.hasPermission(Permission.APPLY_PERCENTAGE_DISCOUNT)
                && !SessionManager.hasPermission(Permission.APPLY_FIXED_DISCOUNT)) {
            return BillResult.fail("You do not have permission to apply bill discounts.");
        }

        BigDecimal amount;
        if (isPercent) {
            if (discountValue.compareTo(BigDecimal.valueOf(100)) > 0)
                return BillResult.fail("Discount cannot exceed 100%.");
            amount = bill.getSubtotal()
                        .multiply(discountValue)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            if (discountValue.compareTo(bill.getSubtotal()) > 0)
                return BillResult.fail("Discount cannot exceed subtotal.");
            amount = discountValue;
        }

        bill.setDiscountAmount(amount);
        bill.recalculate();
        logger.debug("Bill discount applied: {} {}", discountValue, isPercent ? "%" : "fixed");
        return BillResult.ok("Bill discount applied.");
    }

    // ── Finalize Bill ─────────────────────────────────────────────────────────

    /**
     * Finalizes and persists the bill.
     * - Validates the bill
     * - Generates a proper bill number
     * - Saves bill + items in a transaction
     * - Deducts stock for each item
     * - Updates customer credit if needed
     *
     * @param bill         The bill to finalize
     * @param paidAmount   Amount tendered by the customer
     * @param paymentType  Payment method
     */
    public BillResult finalizeBill(Bill bill, BigDecimal paidAmount, Bill.PayType paymentType) {

        // Validate
        if (bill.getItems().isEmpty()) {
            return BillResult.fail("Cannot finalize an empty bill.");
        }
        bill.recalculate();

        if (paymentType != Bill.PayType.CREDIT
                && paidAmount.compareTo(bill.getTotalAmount()) < 0) {
            return BillResult.fail("Paid amount is less than the total.");
        }

        // Set final values
        String finalBillNumber = generateFinalBillNumber();
        bill.setBillNumber(finalBillNumber);
        bill.setStatus(Bill.Status.FINALIZED);
        bill.setPaymentType(paymentType);
        bill.setPaidAmount(paidAmount);
        bill.recalculate(); // recompute change
        bill.setFinalizedAt(LocalDateTime.now());

        // Persist to DB — fall back to offline queue if DB is unreachable
        boolean saved = billRepo.saveBill(bill);
        if (!saved) {
            // Try offline queue
            boolean queued = com.minimartpos.network.OfflineSync.getInstance().enqueue(bill);
            if (queued) {
                logger.warn("DB save failed — bill {} queued offline for later sync.",
                            bill.getBillNumber());
                return BillResult.ok(finalBillNumber + " [OFFLINE]");
            }
            return BillResult.fail("Failed to save bill to database. Please try again.");
        }

        // Deduct stock for each item (atomic per product)
        for (BillItem item : bill.getItems()) {
            boolean stockOk = stockService.deductStock(
                    item.getProductId(), item.getQuantity(),
                    bill.getId(), bill.getCashierId());
            if (!stockOk) {
                logger.warn("Stock deduction failed for product {} on bill {}",
                            item.getProductId(), bill.getBillNumber());
            }
        }

        // Broadcast sync events to other machines
        try {
            com.minimartpos.network.SyncManager sm =
                com.minimartpos.network.SyncManager.getInstance();
            sm.notifyBillFinalized(bill.getId());
            for (BillItem item : bill.getItems()) {
                sm.notifyStockChanged(item.getProductId());
            }
        } catch (Exception e) {
            logger.debug("Sync broadcast error (non-fatal): {}", e.getMessage());
        }

        logger.info("Bill finalized: {} | Total: {} | Payment: {} | Cashier: {}",
                    bill.getBillNumber(), bill.getTotalAmount(), paymentType,
                    SessionManager.getCurrentUser().getUsername());

        return BillResult.ok(finalBillNumber);
    }

    // ── Void Bill ─────────────────────────────────────────────────────────────

    /**
     * Voids a finalized bill with a reason.
     * Requires VOID_BILL permission.
     */
    public BillResult voidBill(Bill bill, String reason) {
        if (!SessionManager.hasPermission(Permission.VOID_BILL)) {
            return BillResult.fail("You do not have permission to void bills.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            return BillResult.fail("A reason is required to void a bill.");
        }
        if (bill.getStatus() == Bill.Status.VOIDED) {
            return BillResult.fail("Bill is already voided.");
        }

        bill.setStatus(Bill.Status.VOIDED);
        bill.setVoidReason(reason.trim());
        billRepo.updateBillStatus(bill);

        // Reverse stock deductions
        for (BillItem item : bill.getItems()) {
            stockService.addStock(item.getProductId(), item.getQuantity(),
                                  bill.getId(), bill.getCashierId(), "RETURN");
        }

        logger.info("Bill voided: {} by {} — reason: {}",
                    bill.getBillNumber(),
                    SessionManager.getCurrentUser().getUsername(),
                    reason);
        return BillResult.ok("Bill voided.");
    }

    // ── Hold Bill ─────────────────────────────────────────────────────────────

    /**
     * In-memory store of held bills, keyed by a hold label (e.g. "Customer A").
     * Bills are held per-cashier session; held bills are lost on logout.
     * Map: holdLabel → Bill
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Bill> heldBills =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Parks the current active bill so the cashier can serve a different customer.
     * The bill is stored in memory with an optional label.
     * A fresh empty bill is returned for the next customer.
     *
     * @param bill   The currently active bill (must have at least one item)
     * @param label  A short label to identify the held bill (e.g. customer name or "Bill 1")
     * @return BillResult.ok if held; BillResult.fail if bill is empty
     */
    public BillResult holdBill(Bill bill, String label) {
        if (bill.getItems().isEmpty()) {
            return BillResult.fail("Cannot hold an empty bill.");
        }
        String key = label != null && !label.isBlank()
            ? label.trim()
            : "Held " + (heldBills.size() + 1) + " — " + bill.getBillNumber();
        heldBills.put(key, bill);
        logger.info("Bill held: {} items={} label='{}'",
            bill.getBillNumber(), bill.getItems().size(), key);
        return BillResult.ok(key);
    }

    /**
     * Retrieves a held bill by label, removing it from the hold store.
     *
     * @param label The hold label returned by holdBill()
     * @return The held Bill, or empty if not found
     */
    public java.util.Optional<Bill> retrieveHeldBill(String label) {
        Bill b = heldBills.remove(label);
        if (b != null) logger.info("Bill retrieved from hold: '{}'", label);
        return java.util.Optional.ofNullable(b);
    }

    /**
     * Returns all currently held bills as an immutable map (label → bill).
     */
    public java.util.Map<String, Bill> getHeldBills() {
        return java.util.Collections.unmodifiableMap(heldBills);
    }

    /**
     * Clears all held bills (called on logout/shift end).
     */
    public void clearHeldBills() {
        int count = heldBills.size();
        heldBills.clear();
        if (count > 0) logger.info("Cleared {} held bills on logout.", count);
    }

    // ── Cash limit helper ─────────────────────────────────────────────────────

    /**
     * Returns total CASH collected today by a cashier (for cash limit monitoring).
     */
    public java.math.BigDecimal getTodayCashCollected(int cashierId) {
        return billRepo.getTodayCashCollected(cashierId);
    }

    // ── Result Type ───────────────────────────────────────────────────────────

    public static class BillResult {
        private final boolean success;
        private final String  message;

        private BillResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static BillResult ok(String message)   { return new BillResult(true,  message); }
        public static BillResult fail(String message) { return new BillResult(false, message); }

        public boolean isSuccess() { return success; }
        public String  getMessage(){ return message; }
    }
}
