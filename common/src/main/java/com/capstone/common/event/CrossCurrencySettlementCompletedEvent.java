package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by Accounts Service outbox relay after the row-locked debit and credit mutations commit in Oracle.
 * Consumed by Transaction Service to append ledger audit lines in PostgreSQL and commit transaction_master.
 */
public record CrossCurrencySettlementCompletedEvent(
        UUID txnId,
        String sourceAccountId,
        String destAccountId,
        String sourceCurrency,
        String destCurrency,
        BigDecimal sourceAmount,
        BigDecimal fxRate,
        BigDecimal destAmount,
        BigDecimal sourceBalanceAfter,
        BigDecimal destBalanceAfter,
        Instant settledAt
) {
    public CrossCurrencySettlementCompletedEvent {
        if (settledAt == null) {
            settledAt = Instant.now();
        }
    }
}
