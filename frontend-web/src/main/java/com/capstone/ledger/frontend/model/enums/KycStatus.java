package com.capstone.ledger.frontend.model.enums;

/**
 * KYC verification status for Customer Onboarding:
 * PENDING_VERIFICATION, VERIFIED, REJECTED
 */
public enum KycStatus {
    PENDING_VERIFICATION("Pending Review", "warning"),
    VERIFIED("Verified", "success"),
    REJECTED("Action Required", "danger");

    private final String displayName;
    private final String badgeClass;

    KycStatus(String displayName, String badgeClass) {
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

