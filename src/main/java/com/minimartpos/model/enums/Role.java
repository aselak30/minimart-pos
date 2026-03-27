package com.minimartpos.model.enums;

/**
 * User roles in the system.
 */
public enum Role {
    ADMIN("Administrator"),
    CASHIER("Cashier");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
