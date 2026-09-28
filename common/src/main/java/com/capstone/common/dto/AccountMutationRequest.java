package com.capstone.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AccountMutationRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
        @Digits(integer = 14, fraction = 4, message = "Amount may have at most 14 integer and 4 fractional digits")
        BigDecimal amount,

        String txnId,
        String txnType
) {}

