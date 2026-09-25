
package com.capstone.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * accountId -> ACCOUNT_MASTER.account_id (VARCHAR2 36 UUID).
 * customerId -> ACCOUNT_MASTER.customer_id (VARCHAR2 36 UUID).
 * balanceAmount -> ACCOUNT_MASTER.balance_amount NUMBER(18,4).
 */
public record AccountDTO(
        String accountId,
        String customerId,
        String accountType,
        String accountStatus,
        BigDecimal balanceAmount,
        String currencyCode,
        LocalDateTime createdAt
) {
}

