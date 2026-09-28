package com.bank.reconciliation.event;

import com.bank.reconciliation.entity.postgres.ReconRunAudit;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Published on "reconciliation.run.completed" once a batch finishes, win or fail. */
public record ReconciliationRunCompletedEvent(
        UUID runId,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        int totalTxnChecked,
        int totalMatched,
        int totalExceptions,
        String runStatus
) {
    public static ReconciliationRunCompletedEvent from(ReconRunAudit run) {
        return new ReconciliationRunCompletedEvent(
                run.getRunId(),
                run.getWindowStart(),
                run.getWindowEnd(),
                run.getTotalTxnChecked(),
                run.getTotalMatched(),
                run.getTotalExceptions(),
                run.getRunStatus().name()
        );
    }
}
