package com.bank.reconciliation.entity.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recon_result_audit")
@Getter
@Setter
@NoArgsConstructor
public class ReconResultAudit {

    public enum ReconStatus { MATCHED, EXCEPTION }

    public enum ExceptionType {
        MISSING_LEDGER_ENTRY, ORPHAN_LEDGER_ENTRY, AMOUNT_MISMATCH,
        ACCOUNT_MISMATCH, STATUS_MISMATCH, DUPLICATE_ENTRY, LATE_POSTING,
        CURRENCY_MISMATCH
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "result_id", updatable = false, nullable = false)
    private UUID resultId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "txn_id", nullable = false, length = 36)
    private String txnId;

    @Column(name = "account_id", length = 36)
    private String accountId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "mutation_uuid")
    private UUID mutationUuid;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "duplicate_mutation_uuid")
    private UUID duplicateMutationUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "recon_status", nullable = false, length = 20)
    private ReconStatus reconStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", length = 30)
    private ExceptionType exceptionType;

    @Column(name = "expected_currency_code", length = 3)
    private String expectedCurrencyCode;

    @Column(name = "actual_currency_code", length = 3)
    private String actualCurrencyCode;

    @Column(name = "expected_amount", precision = 18, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 18, scale = 4)
    private BigDecimal actualAmount;

    // DB-generated (GENERATED ALWAYS AS ... STORED) - read-only from JPA's side
    @Column(name = "variance_amount", precision = 18, scale = 4, insertable = false, updatable = false)
    private BigDecimal varianceAmount;

    @Column(name = "txn_status", length = 20)
    private String txnStatus;

    @Column(name = "ledger_audit_state", length = 20)
    private String ledgerAuditState;

    @Column(name = "txn_completed_at")
    private OffsetDateTime txnCompletedAt;

    @Column(name = "ledger_posted_at")
    private OffsetDateTime ledgerPostedAt;

    @Column(name = "posting_lag_seconds")
    private Integer postingLagSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity = Severity.LOW;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (resultId == null) resultId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
