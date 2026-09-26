package com.capstone.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Payload sent to Accounts Service to perform a balance mutation under lock.
 */
public record AccountMutationRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
        BigDecimal amount,

        String txnId,

        String txnType
) {}
