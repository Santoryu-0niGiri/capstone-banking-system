package com.capstone.common.dto;

import java.math.BigDecimal;

/**
 * Result returned by Accounts Service after a successful debit or credit mutation.
 */
public record AccountMutationResponse(
        String accountId,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        BigDecimal appliedDelta,
        String currencyCode
) {}
