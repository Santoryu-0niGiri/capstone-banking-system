package com.capstone.ledger.frontend.model.enums;

/**
 * Transaction type matching ERD TRANSACTION_MASTER.txn_type:
 * WITHDRAWAL, DEPOSIT, TRANSFER
 */
public enum TransactionType {
    DEPOSIT("Deposit", "success"),
    WITHDRAWAL("Withdrawal", "danger"),
    TRANSFER("Fund Transfer", "primary");

    private final String displayName;
    private final String badgeClass;

    TransactionType(String displayName, String badgeClass) {
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

