package com.bank.reconciliation.dto;

import com.bank.reconciliation.entity.postgres.LedgerMutationAudit;

import java.util.List;

/**
 * One expected leg paired with the actual ledger_mutation_audit rows
 * found for its (txn_id, mutation_type) key - normally 0 (missing) or
 * 1 (the happy path); more than 1 is a DUPLICATE_ENTRY anomaly.
 */
public record LegMatchCandidate(
        ExpectedLeg expected,
        List<LedgerMutationAudit> actualLegs
) {
}
