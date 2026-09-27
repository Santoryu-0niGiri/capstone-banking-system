package com.capstone.common.dto;

import java.math.BigDecimal;

public record AccountMutationResponse(
        String accountId,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        BigDecimal appliedDelta,
        String currencyCode
) {}

