package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by Transaction Service outbox relay when a cross-currency transfer is initiated.
 * Consumed by ForEx Service to compute rate conversion and destination amount.
 */
public record ForexConversionRequestedEvent(
        UUID txnId,
        String sourceAccountId,
        String destAccountId,
        String sourceCurrency,
        String destCurrency,
        BigDecimal sourceAmount,
        Instant requestedAt
) {
    public ForexConversionRequestedEvent {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
