package com.bank.reconciliation.dto;

import java.time.OffsetDateTime;

/** Body for POST /api/recon/runs - lets ops trigger an end-of-day sweep over an arbitrary window. */
public record RunReconciliationRequest(
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd
) {
}
