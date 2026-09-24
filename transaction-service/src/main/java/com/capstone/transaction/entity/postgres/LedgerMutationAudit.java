
package com.capstone.transaction.entity.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to LEDGER_MUTATION_AUDIT (PostgreSQL 15+).
 *
 * This table is APPEND-ONLY — a DB-level trigger
 * (trg_ledger_mutation_audit_append_only) blocks all UPDATE and DELETE.
 * The repository therefore exposes ONLY save/saveAll; no update or delete
 * methods are declared on LedgerMutationAuditRepository.
 *
 * One row per debit or credit leg of a transaction (double-entry):
 *   WITHDRAWAL  -> one DEBIT  row  (debit_account_id side)
 *   DEPOSIT     -> one CREDIT row  (credit_account_id side)
 *   TRANSFER    -> one DEBIT  row  + one CREDIT row
 *
 * mutation_type CHECK : DEBIT | CREDIT
 * txn_type      CHECK : WITHDRAWAL | DEPOSIT | TRANSFER
 * audit_state   CHECK : PENDING | COMMITTED | ROLLED_BACK
 * mutation_amount must be > 0 (DDL CHECK + @Positive at API layer)
 */
@Entity
@Table(name = "ledger_mutation_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerMutationAudit {

    // DB DEFAULT uuid_generate_v4() — GenerationType.UUID lets Hibernate
    // assign a value before INSERT so it is never sent as NULL
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mutation_uuid", updatable = false, nullable = false)
    private UUID mutationUuid;

    // References TRANSACTION_MASTER.txn_id (Oracle) — no cross-engine FK,
    // indexed instead (ix_ledger_audit_txn_id)
    @Column(name = "txn_id", nullable = false, length = 36)
    private String txnId;

    // References ACCOUNT_MASTER.account_id (Oracle) — indexed
    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    // NUMERIC(18,4) — matches NUMBER(18,4) on the Oracle side so the two
    // legs can never disagree on precision
    @Column(name = "mutation_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal mutationAmount;

    // DEBIT | CREDIT — determines which side of the double-entry this row is
    @Column(name = "mutation_type", nullable = false, length = 10)
    private String mutationType;

    // WITHDRAWAL | DEPOSIT | TRANSFER — mirrors TRANSACTION_MASTER.txn_type
    @Column(name = "txn_type", nullable = false, length = 30)
    private String txnType;

    // PENDING | COMMITTED | ROLLED_BACK — mirrors TRANSACTION_MASTER.txn_status
    @Column(name = "audit_state", nullable = false, length = 20)
    private String auditState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

