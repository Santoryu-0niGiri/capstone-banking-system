package com.capstone.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID txnId, Long acctNo, String txnType, BigDecimal amount,
                                   BigDecimal balanceAfter, String status, Instant timestamp) {
}
