
package com.capstone.transaction.model;

import java.math.BigDecimal;

public record TransferResult(
        MutationResult sourceResult,
        MutationResult destResult,
        Boolean isCrossCurrency,
        BigDecimal fxRate,
        BigDecimal destAmount,
        String sourceCurrency,
        String targetCurrency
) {
        public TransferResult(MutationResult sourceResult, MutationResult destResult) {
                this(sourceResult, destResult, false, null, null, null, null);
        }
}

