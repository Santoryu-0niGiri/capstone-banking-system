package com.bank.reconciliation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Body for POST /api/recon/runs - lets ops trigger an end-of-day sweep over an arbitrary window. */
public record RunReconciliationRequest(
        @NotNull OffsetDateTime windowStart,
        @NotNull OffsetDateTime windowEnd
) {
}
