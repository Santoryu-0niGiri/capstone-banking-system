package com.bank.reconciliation.dto;

import com.bank.reconciliation.entity.postgres.ReconRunAudit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RunSummaryResponse(
        UUID runId,
        String status,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int totalChecked,
        int totalMatched,
        int totalExceptions
) {
    public static RunSummaryResponse from(ReconRunAudit run) {
        return new RunSummaryResponse(
                run.getRunId(),
                run.getRunStatus().name(),
                run.getWindowStart(),
                run.getWindowEnd(),
                run.getRunStartedAt(),
                run.getRunCompletedAt(),
                run.getTotalTxnChecked(),
                run.getTotalMatched(),
                run.getTotalExceptions()
        );
    }
}
