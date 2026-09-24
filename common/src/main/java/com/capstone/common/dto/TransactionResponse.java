
package com.capstone.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * accountId is now a String UUID matching ACCOUNT_MASTER.account_id.
 * txnType mirrors TRANSACTION_MASTER.txn_type (WITHDRAWAL|DEPOSIT|TRANSFER).
 * txnStatus mirrors TRANSACTION_MASTER.txn_status (PENDING|COMMITTED|ROLLED_BACK).
 */
public record TransactionResponse(
        UUID txnId,
        String accountId,
        String txnType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String txnStatus,
        Instant timestamp
) {
}

