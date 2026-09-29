package com.bank.reconciliation.dto;

import com.bank.reconciliation.entity.postgres.ReconResultAudit;
import com.bank.reconciliation.entity.postgres.ReconRunAudit;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
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
        int totalExceptions,
        List<ReconResultAudit> results
) {
    public static RunSummaryResponse from(ReconRunAudit run) {
        return from(run, Collections.emptyList());
    }

    public static RunSummaryResponse from(ReconRunAudit run, List<ReconResultAudit> results) {
        return new RunSummaryResponse(
                run.getRunId(),
                run.getRunStatus().name(),
                run.getWindowStart(),
                run.getWindowEnd(),
                run.getRunStartedAt(),
                run.getRunCompletedAt(),
                run.getTotalTxnChecked(),
                run.getTotalMatched(),
                run.getTotalExceptions(),
                results != null ? results : Collections.emptyList()
        );
    }
}
