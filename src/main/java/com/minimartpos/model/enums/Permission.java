package com.minimartpos.model.enums;

/**
 * All configurable permissions in the system.
 *
 * Grouped by category to mirror the specification.
 * Admins automatically have all permissions.
 * Cashiers only have permissions explicitly granted by admin.
 */
public enum Permission {

    // ── A. Price & Cost Visibility ────────────────────────────────────────────
    VIEW_COST_PRICE          ("View product cost price"),
    VIEW_PROFIT_MARGIN       ("View profit margin per item"),
    VIEW_BILL_PROFIT         ("View total profit on current bill"),
    VIEW_PURCHASE_PRICE_HIST ("View historical purchase prices"),
    VIEW_SUPPLIER_COST       ("View supplier cost information"),

    // ── B. Cart Modification ──────────────────────────────────────────────────
    CHANGE_SELLING_PRICE     ("Change product selling price in cart"),
    OVERRIDE_PRICE           ("Override system price with manual entry"),
    ADD_NEGATIVE_QUANTITY    ("Add negative quantities (returns)"),
    REMOVE_FINALIZED_ITEM    ("Remove items after bill finalization"),
    ADD_CUSTOM_DISCOUNT      ("Add custom discounts beyond standard limit"),
    ADD_FREE_ITEM            ("Add free items (zero price)"),
    SPLIT_BILL               ("Split bill items across multiple transactions"),

    // ── C. Discount Management ────────────────────────────────────────────────
    APPLY_PERCENTAGE_DISCOUNT("Apply percentage discount"),
    APPLY_FIXED_DISCOUNT     ("Apply fixed amount discount"),
    APPLY_LINE_ITEM_DISCOUNT ("Apply line-item discounts"),
    APPLY_BILL_DISCOUNT      ("Apply bill-level discounts"),
    OVERRIDE_DISCOUNT_LIMIT  ("Override discount limits"),

    // ── D. Product Management ─────────────────────────────────────────────────
    ADD_PRODUCT_DURING_BILLING("Add new products during billing"),
    UPDATE_STOCK_MANUALLY    ("Update stock levels manually"),
    CREATE_QUICK_PRODUCT     ("Create quick products (without full details)"),
    VIEW_LOW_STOCK_ALERTS    ("View low stock alerts"),
    REQUEST_STOCK_TRANSFER   ("Request stock transfers"),

    // ── E. Customer Management ────────────────────────────────────────────────
    ADD_CUSTOMER             ("Add new customers"),
    EDIT_CUSTOMER            ("Edit customer details"),
    VIEW_CUSTOMER_CREDIT     ("View customer credit history"),
    APPROVE_CREDIT           ("Approve credit within limit"),
    WRITE_OFF_CREDIT         ("Write off small credit amounts"),

    // ── F. Bill Management ────────────────────────────────────────────────────
    DELETE_OWN_BILL          ("Delete bills (own shift only)"),
    DELETE_ANY_BILL          ("Delete any bill (any cashier)"),
    EDIT_BILL_AFTER_PRINT    ("Edit bills after printing"),
    REPRINT_OLD_BILL         ("Reprint old bills"),
    EMAIL_BILL               ("Email bills to customers"),
    VOID_BILL                ("Void bills with reason"),

    // ── G. Advanced ───────────────────────────────────────────────────────────
    VIEW_OTHER_CASHIER_SALES ("View other cashier's sales"),
    ACCESS_BASIC_REPORTS     ("Access basic reports (own sales only)"),
    END_OF_DAY_RECONCILIATION("Perform end-of-day reconciliation"),
    OPEN_CASH_DRAWER         ("Open cash drawer without sale"),
    ACCESS_SYSTEM_SETTINGS   ("Access system settings (limited)");

    // ─────────────────────────────────────────────────────────────────────────

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns the permission category label for grouping in the UI.
     */
    public String getCategory() {
        String name = this.name();
        if (name.startsWith("VIEW_COST") || name.startsWith("VIEW_PROFIT") ||
            name.startsWith("VIEW_BILL_PROFIT") || name.startsWith("VIEW_PURCHASE") ||
            name.startsWith("VIEW_SUPPLIER")) {
            return "Price & Cost Visibility";
        } else if (name.startsWith("CHANGE_") || name.startsWith("OVERRIDE_PRICE") ||
                   name.startsWith("ADD_NEGATIVE") || name.startsWith("REMOVE_FINALIZED") ||
                   name.startsWith("ADD_CUSTOM") || name.startsWith("ADD_FREE") ||
                   name.startsWith("SPLIT_")) {
            return "Cart Modification";
        } else if (name.contains("DISCOUNT")) {
            return "Discount Management";
        } else if (name.startsWith("ADD_PRODUCT") || name.startsWith("UPDATE_STOCK") ||
                   name.startsWith("CREATE_QUICK") || name.startsWith("VIEW_LOW_STOCK") ||
                   name.startsWith("REQUEST_STOCK")) {
            return "Product Management";
        } else if (name.startsWith("ADD_CUSTOMER") || name.startsWith("EDIT_CUSTOMER") ||
                   name.startsWith("VIEW_CUSTOMER") || name.startsWith("APPROVE_CREDIT") ||
                   name.startsWith("WRITE_OFF")) {
            return "Customer Management";
        } else if (name.startsWith("DELETE_") || name.startsWith("EDIT_BILL") ||
                   name.startsWith("REPRINT") || name.startsWith("EMAIL_BILL") ||
                   name.startsWith("VOID_")) {
            return "Bill Management";
        } else {
            return "Advanced";
        }
    }
}
