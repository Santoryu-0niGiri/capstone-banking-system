package com.bank.reconciliation.event;

import com.bank.reconciliation.entity.postgres.ReconResultAudit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published on the "reconciliation.discrepancy" topic (see architecture
 * diagram: Recon -.-> Kafka -.-> Notification). One event per exception
 * row, so Notification can alert without polling recon_result_audit.
 */
public record ReconciliationDiscrepancyEvent(
        UUID resultId,
        UUID runId,
        String txnId,
        String accountId,
        String exceptionType,
        String severity,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        BigDecimal varianceAmount,
        OffsetDateTime detectedAt
) {
    public static ReconciliationDiscrepancyEvent from(ReconResultAudit result) {
        return new ReconciliationDiscrepancyEvent(
                result.getResultId(),
                result.getRunId(),
                result.getTxnId(),
                result.getAccountId(),
                result.getExceptionType() != null ? result.getExceptionType().name() : null,
                result.getSeverity().name(),
                result.getExpectedAmount(),
                result.getActualAmount(),
                result.getVarianceAmount(),
                result.getCreatedAt()
        );
    }
}
