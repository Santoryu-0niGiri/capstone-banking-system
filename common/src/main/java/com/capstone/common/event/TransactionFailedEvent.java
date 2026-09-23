
package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionFailedEvent(
        UUID txnId,
        String accountId,
        String counterpartyAccountId,
        String txnType,
        BigDecimal amount,
        String reason,
        Instant occurredAt
) {
}

