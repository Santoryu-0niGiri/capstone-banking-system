package com.capstone.transaction.model;

import java.math.BigDecimal;

/**
 * @param appliedDelta the signed amount applied to the account balance
 *                      (negative for a debit/transfer-out, positive for a
 *                      credit/transfer-in) — negating it is exactly the
 *                      compensating mutation.
 */
public record MutationResult(BigDecimal balanceBefore, BigDecimal balanceAfter, BigDecimal appliedDelta) {
}
