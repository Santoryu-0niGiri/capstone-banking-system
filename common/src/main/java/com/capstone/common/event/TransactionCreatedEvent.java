package com.capstone.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCreatedEvent(UUID txnId, Long acctNo, Long counterpartyAcctNo,
                                       String txnType, BigDecimal amount, Instant occurredAt) {
}
