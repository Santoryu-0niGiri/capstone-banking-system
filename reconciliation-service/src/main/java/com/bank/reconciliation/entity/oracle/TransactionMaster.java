package com.bank.reconciliation.entity.oracle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Read-only mapping onto Oracle transaction_master, owned by
 * transaction-service. Matches the real DDL: WITHDRAWAL only
 * populates debit_account_id, DEPOSIT only credit_account_id,
 * TRANSFER populates both - never a single flat "account_id".
 * txn_status shares its vocabulary (PENDING/COMMITTED/ROLLED_BACK)
 * with ledger_mutation_audit.audit_state on the Postgres side, which
 * is exactly what makes a STATUS_MISMATCH check meaningful.
 */
@Entity
@Table(name = "TRANSACTION_MASTER")
@Getter
@Setter
public class TransactionMaster {

    @Id
    @Column(name = "TXN_ID", length = 36)
    private String txnId;

    @Column(name = "TXN_TYPE", length = 30, nullable = false)
    private String txnType; // WITHDRAWAL / DEPOSIT / TRANSFER

    @Column(name = "DEBIT_ACCOUNT_ID", length = 36)
    private String debitAccountId;

    @Column(name = "CREDIT_ACCOUNT_ID", length = 36)
    private String creditAccountId;

    @Column(name = "MUTATION_AMOUNT", precision = 18, scale = 4, nullable = false)
    private BigDecimal mutationAmount;

    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "IS_CROSS_CURRENCY", length = 1)
    private String isCrossCurrency;

    @Column(name = "FX_RATE", precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "DEST_AMOUNT", precision = 18, scale = 4)
    private BigDecimal destAmount;

    @Column(name = "DEST_CURRENCY_CODE", length = 3)
    private String destCurrencyCode;

    @Column(name = "TXN_STATUS", length = 20, nullable = false)
    private String txnStatus; // PENDING / COMMITTED / ROLLED_BACK

    @Column(name = "INITIATED_AT", nullable = false)
    private OffsetDateTime initiatedAt;

    @Column(name = "COMPLETED_AT")
    private OffsetDateTime completedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;
}
