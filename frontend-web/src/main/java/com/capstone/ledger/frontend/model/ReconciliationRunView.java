package com.capstone.ledger.frontend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * View model representing a periodic batch reconciliation run matching
 * RECON_RUN_AUDIT in the ERD.
 */
public class ReconciliationRunView {

    private String runId;
    private LocalDateTime runStartedAt;
    private LocalDateTime runCompletedAt;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private int totalTxnChecked;
    private int totalMatched;
    private int totalExceptions;
    private String runStatus; // RUNNING, COMPLETED, FAILED
    private List<ReconciliationResultView> results = new ArrayList<>();

    public ReconciliationRunView() {
    }

    public ReconciliationRunView(String runId, LocalDateTime runStartedAt, LocalDateTime runCompletedAt,
                                 LocalDateTime windowStart, LocalDateTime windowEnd, int totalTxnChecked,
                                 int totalMatched, int totalExceptions, String runStatus) {
        this.runId = runId;
        this.runStartedAt = runStartedAt;
        this.runCompletedAt = runCompletedAt;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.totalTxnChecked = totalTxnChecked;
        this.totalMatched = totalMatched;
        this.totalExceptions = totalExceptions;
        this.runStatus = runStatus;
    }

    public String getStatusBadgeClass() {
        if ("COMPLETED".equalsIgnoreCase(runStatus)) return "success";
        if ("RUNNING".equalsIgnoreCase(runStatus)) return "primary";
        return "danger";
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public LocalDateTime getRunStartedAt() { return runStartedAt; }
    public void setRunStartedAt(LocalDateTime runStartedAt) { this.runStartedAt = runStartedAt; }

    public LocalDateTime getRunCompletedAt() { return runCompletedAt; }
    public void setRunCompletedAt(LocalDateTime runCompletedAt) { this.runCompletedAt = runCompletedAt; }

    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }

    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }

    public int getTotalTxnChecked() { return totalTxnChecked; }
    public void setTotalTxnChecked(int totalTxnChecked) { this.totalTxnChecked = totalTxnChecked; }

    public int getTotalMatched() { return totalMatched; }
    public void setTotalMatched(int totalMatched) { this.totalMatched = totalMatched; }

    public int getTotalExceptions() { return totalExceptions; }
    public void setTotalExceptions(int totalExceptions) { this.totalExceptions = totalExceptions; }

    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }

    public List<ReconciliationResultView> getResults() { return results; }
    public void setResults(List<ReconciliationResultView> results) { this.results = results; }
}

