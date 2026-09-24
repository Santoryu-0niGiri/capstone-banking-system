package com.capstone.ledger.frontend.model.enums;

/**
 * Account types matching ERD ACCOUNT_MASTER.account_type:
 * SAVINGS, CHECKING, WALLET
 */
public enum AccountType {
    SAVINGS("Savings Account"),
    CHECKING("Checking Account"),
    WALLET("Digital Wallet");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

