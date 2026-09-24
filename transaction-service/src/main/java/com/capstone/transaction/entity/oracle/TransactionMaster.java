
package com.capstone.transaction.entity.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to TRANSACTION_MASTER (Oracle XE 21c).
 *
 * One row per /api/v1/ledger/mutate request, persisted in Oracle
 * inside the same PESSIMISTIC_WRITE transaction that updates
 * ACCOUNT_MASTER, so the two writes are atomic.
 *
 * txn_type   CHECK : WITHDRAWAL | DEPOSIT | TRANSFER
 * txn_status CHECK : PENDING | COMMITTED | ROLLED_BACK
 *
 * DDL nullability rules enforced at service layer:
 *   WITHDRAWAL -> debitAccountId  non-null, creditAccountId null
 *   DEPOSIT    -> creditAccountId non-null, debitAccountId  null
 *   TRANSFER   -> both non-null, must differ
 *
 * mutation_amount mirrors @Digits(integer=14, fraction=4) + @Positive
 * at the controller layer (NUMBER(18,4) in DDL).
 */
@Entity
@Table(name = "transaction_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionMaster {

    @Id
    @Column(name = "txn_id", length = 36, updatable = false, nullable = false)
    private String txnId;

    @Column(name = "txn_type", nullable = false, length = 30)
    private String txnType;

    // Nullable: only WITHDRAWAL and TRANSFER populate this side
    @Column(name = "debit_account_id", length = 36)
    private String debitAccountId;

    // Nullable: only DEPOSIT and TRANSFER populate this side
    @Column(name = "credit_account_id", length = 36)
    private String creditAccountId;

    @Column(name = "mutation_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal mutationAmount;

    // DEFAULT 'PENDING' in DDL; set to COMMITTED after dual-write succeeds
    @Column(name = "txn_status", nullable = false, length = 20)
    private String txnStatus;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}

