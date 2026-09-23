
package com.capstone.transaction.model;

import java.math.BigDecimal;

/**
 * Captures the before/after state of a single balance mutation.
 *
 * @param appliedDelta signed amount written to balance_amount:
 *                     negative for DEBIT/transfer-out, positive for CREDIT/transfer-in.
 *                     Negating it gives the exact compensating delta on rollback.
 */
public record MutationResult(BigDecimal balanceBefore, BigDecimal balanceAfter, BigDecimal appliedDelta) {
}

