
package com.capstone.transaction.repository.postgres;

import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * APPEND-ONLY: exposes only save/saveAll inherited from JpaRepository.
 * No update or delete methods are declared here — the DB-level trigger
 * (trg_ledger_mutation_audit_append_only) would reject them anyway, but
 * omitting them at the repository level makes the intent explicit and
 * prevents accidental misuse.
 *
 * txnId  is a String matching TRANSACTION_MASTER.txn_id (VARCHAR2 36).
 * accountId is a String matching ACCOUNT_MASTER.account_id.
 */
public interface LedgerMutationAuditRepository extends JpaRepository<LedgerMutationAudit, UUID> {

    // Used by TransactionController GET /{txnId} to surface audit legs
    List<LedgerMutationAudit> findByTxnId(String txnId);

    // Used by reconciliation queries to find all legs for an account
    List<LedgerMutationAudit> findByAccountId(String accountId);
}

