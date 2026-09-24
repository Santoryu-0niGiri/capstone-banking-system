package com.capstone.ledger.frontend.model.enums;

/**
 * Account status matching ERD ACCOUNT_MASTER.account_status:
 * ACTIVE, FROZEN, CLOSED
 */
public enum AccountStatus {
    ACTIVE("Active", "success"),
    FROZEN("Frozen", "warning"),
    CLOSED("Closed", "secondary");

    private final String displayName;
    private final String badgeClass;

    AccountStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}

