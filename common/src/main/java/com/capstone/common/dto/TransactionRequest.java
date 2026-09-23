package com.capstone.common.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransactionRequest(

        @NotNull(message = "Source account number is required")
        Long acctNo,

        @NotNull(message = "Destination account number is required for a transfer; null otherwise")
        Long counterpartyAcctNo,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 14, fraction = 4, message = "Amount may have at most 14 integer and 4 fractional digits")
        BigDecimal amount,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {
}
