package com.capstone.ledger.frontend.model.enums;

/**
 * Audit state matching ERD LEDGER_MUTATION_AUDIT.audit_state:
 * PENDING, COMMITTED, ROLLED_BACK
 */
public enum AuditState {
    PENDING("Pending", "warning"),
    COMMITTED("Committed", "success"),
    ROLLED_BACK("Rolled Back", "danger");

    private final String displayName;
    private final String badgeClass;

    AuditState(String displayName, String badgeClass) {
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

