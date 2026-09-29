package com.bank.reconciliation.entity.postgres;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Owned by Transaction Service in the real deployment; recon only
 * reads it (no save/update exposed in the repository). Append-only at
 * the DB layer via trg_ledger_mutation_audit_append_only - matches the
 * real DDL exactly: mutation_uuid, txn_id, account_id, mutation_amount,
 * mutation_type (DEBIT/CREDIT), txn_type, audit_state
 * (PENDING/COMMITTED/ROLLED_BACK), created_at. There is no separate
 * posted_at column - created_at IS the posting timestamp, since rows
 * are immutable from the moment they're inserted.
 */
@Entity
@Table(name = "ledger_mutation_audit")
@Getter
@Setter
@NoArgsConstructor
public class LedgerMutationAudit {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "mutation_uuid")
    private UUID mutationUuid;

    @Column(name = "txn_id", nullable = false, length = 36)
    private String txnId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "mutation_amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal mutationAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "mutation_type", nullable = false, length = 10)
    private String mutationType; // DEBIT / CREDIT

    @Column(name = "txn_type", nullable = false, length = 30)
    private String txnType; // WITHDRAWAL / DEPOSIT / TRANSFER

    @Column(name = "audit_state", nullable = false, length = 20)
    private String auditState; // PENDING / COMMITTED / ROLLED_BACK

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
