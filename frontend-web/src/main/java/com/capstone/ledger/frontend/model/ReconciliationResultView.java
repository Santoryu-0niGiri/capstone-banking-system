package com.capstone.ledger.frontend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * View model representing a reconciliation discrepancy result matching
 * RECON_RESULT_AUDIT in the ERD.
 */
public class ReconciliationResultView {

    private String resultId;
    private String runId;
    private String txnId;
    private String accountId;
    private String reconStatus; // MATCHED, EXCEPTION
    private String exceptionType;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal varianceAmount;
    private int postingLagSeconds;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private LocalDateTime createdAt;

    public ReconciliationResultView() {
    }

    public ReconciliationResultView(String resultId, String runId, String txnId, String accountId,
                                    String reconStatus, String exceptionType, BigDecimal expectedAmount,
                                    BigDecimal actualAmount, BigDecimal varianceAmount,
                                    int postingLagSeconds, String severity, LocalDateTime createdAt) {
        this.resultId = resultId;
        this.runId = runId;
        this.txnId = txnId;
        this.accountId = accountId;
        this.reconStatus = reconStatus;
        this.exceptionType = exceptionType;
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
        this.varianceAmount = varianceAmount;
        this.postingLagSeconds = postingLagSeconds;
        this.severity = severity;
        this.createdAt = createdAt;
    }

    public String getSeverityBadgeClass() {
        if ("CRITICAL".equalsIgnoreCase(severity)) return "danger";
        if ("HIGH".equalsIgnoreCase(severity)) return "warning";
        if ("MEDIUM".equalsIgnoreCase(severity)) return "info";
        return "secondary";
    }

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getReconStatus() { return reconStatus; }
    public void setReconStatus(String reconStatus) { this.reconStatus = reconStatus; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }

    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }

    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal varianceAmount) { this.varianceAmount = varianceAmount; }

    public int getPostingLagSeconds() { return postingLagSeconds; }
    public void setPostingLagSeconds(int postingLagSeconds) { this.postingLagSeconds = postingLagSeconds; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

