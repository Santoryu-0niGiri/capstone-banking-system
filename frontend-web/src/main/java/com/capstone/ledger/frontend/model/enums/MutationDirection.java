package com.capstone.ledger.frontend.model.enums;

/**
 * Mutation direction matching ERD LEDGER_MUTATION_AUDIT.mutation_type:
 * DEBIT, CREDIT
 */
public enum MutationDirection {
    DEBIT("Debit", "danger", "-"),
    CREDIT("Credit", "success", "+");

    private final String displayName;
    private final String badgeClass;
    private final String sign;

    MutationDirection(String displayName, String badgeClass, String sign) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
        this.sign = sign;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public String getSign() {
        return sign;
    }
}

