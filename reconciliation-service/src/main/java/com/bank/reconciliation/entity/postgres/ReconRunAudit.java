package com.bank.reconciliation.entity.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recon_run_audit")
@Getter
@Setter
@NoArgsConstructor
public class ReconRunAudit {

    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "run_id", updatable = false, nullable = false)
    private UUID runId;

    @Column(name = "run_started_at", nullable = false)
    private OffsetDateTime runStartedAt;

    @Column(name = "run_completed_at")
    private OffsetDateTime runCompletedAt;

    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart;

    @Column(name = "window_end", nullable = false)
    private OffsetDateTime windowEnd;

    @Column(name = "total_txn_checked", nullable = false)
    private int totalTxnChecked;

    @Column(name = "total_matched", nullable = false)
    private int totalMatched;

    @Column(name = "total_exceptions", nullable = false)
    private int totalExceptions;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_status", nullable = false, length = 20)
    private Status runStatus = Status.RUNNING;

    public static ReconRunAudit start(OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        ReconRunAudit run = new ReconRunAudit();
        run.runId = UUID.randomUUID();
        run.runStartedAt = OffsetDateTime.now();
        run.windowStart = windowStart;
        run.windowEnd = windowEnd;
        run.runStatus = Status.RUNNING;
        return run;
    }
}
