package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(UUID txnId, Long acctNo, Long counterpartyAcctNo,
                                         String txnType, BigDecimal amount, BigDecimal balanceAfter,
                                         Instant occurredAt) {
}
