
package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * accountId / counterpartyAccountId are String UUIDs matching
 * TRANSACTION_MASTER.debit_account_id / credit_account_id.
 */
public record TransactionCreatedEvent(
        UUID txnId,
        String accountId,
        String counterpartyAccountId,
        String txnType,
        BigDecimal amount,
        Instant occurredAt
) {
}

