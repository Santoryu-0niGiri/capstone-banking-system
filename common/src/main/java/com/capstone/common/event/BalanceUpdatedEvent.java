package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by Accounts Service outbox relay whenever an account balance is debited or credited.
 */
public record BalanceUpdatedEvent(
        String accountId,
        BigDecimal previousBalance,
        BigDecimal newBalance,
        BigDecimal delta,
        String currencyCode,
        String txnId,
        String txnType,
        Instant timestamp
) {
    public BalanceUpdatedEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
