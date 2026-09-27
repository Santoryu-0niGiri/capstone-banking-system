package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by ForEx Service outbox relay after computing currency conversion from cached rates.
 * Consumed by Accounts Service to execute balance mutations under pessimistic lock.
 */
public record ForexConversionCompletedEvent(
        UUID txnId,
        String sourceAccountId,
        String destAccountId,
        String sourceCurrency,
        String destCurrency,
        BigDecimal sourceAmount,
        BigDecimal fxRate,
        BigDecimal destAmount,
        Instant completedAt
) {
    public ForexConversionCompletedEvent {
        if (completedAt == null) {
            completedAt = Instant.now();
        }
    }
}
