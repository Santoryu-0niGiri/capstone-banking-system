
package com.capstone.common.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * accountId -> ACCOUNT_MASTER.account_id (String UUID).
 * counterpartyAccountId -> second account for TRANSFER (nullable).
 * amount precision mirrors TRANSACTION_MASTER.mutation_amount NUMBER(18,4).
 */
public record TransactionRequest(

        @NotBlank(message = "Account ID is required")
        @Size(max = 36)
        String accountId,

        // Null for WITHDRAWAL / DEPOSIT; required for TRANSFER
        @Size(max = 36)
        String counterpartyAccountId,

        @NotBlank(message = "Transaction type is required")
        @Pattern(regexp = "WITHDRAWAL|DEPOSIT|TRANSFER",
                message = "Transaction type must be WITHDRAWAL, DEPOSIT, or TRANSFER")
        String txnType,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 14, fraction = 4,
                message = "Amount may have at most 14 integer and 4 fractional digits")
        BigDecimal amount,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,

        // FX fields
        String targetCurrency,
        BigDecimal exchangeRate,
        BigDecimal targetAmount,
        BigDecimal feeAmount,
        Boolean isCrossCurrency
) {
    public TransactionRequest(
            String accountId,
            String counterpartyAccountId,
            String txnType,
            BigDecimal amount,
            String idempotencyKey) {
        this(accountId, counterpartyAccountId, txnType, amount, idempotencyKey, null, null, null, null, false);
    }
}

