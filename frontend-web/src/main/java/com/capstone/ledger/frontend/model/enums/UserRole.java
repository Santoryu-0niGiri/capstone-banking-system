package com.capstone.ledger.frontend.model.enums;

/**
 * User role matching ERD APP_USER_MASTER.role:
 * CUSTOMER, ADMIN
 */
public enum UserRole {
    CUSTOMER("Customer"),
    ADMIN("Bank Administrator");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

