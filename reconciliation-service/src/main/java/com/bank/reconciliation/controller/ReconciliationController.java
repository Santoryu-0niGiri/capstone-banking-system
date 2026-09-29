package com.bank.reconciliation.controller;

import com.bank.reconciliation.dto.RunReconciliationRequest;
import com.bank.reconciliation.dto.RunSummaryResponse;
import com.bank.reconciliation.entity.postgres.ReconResultAudit;
import com.bank.reconciliation.entity.postgres.ReconRunAudit;
import com.bank.reconciliation.repository.postgres.ReconResultAuditRepository;
import com.bank.reconciliation.repository.postgres.ReconRunAuditRepository;
import com.bank.reconciliation.service.ReconciliationEngine;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Visibility/trigger surface for the reconciliation service. Read
 * endpoints back the "reconciliation status, break aging, match rate"
 * dashboard use case; the POST lets ops kick off an ad-hoc or
 * end-of-day sweep outside the regular cron.
 */
@RestController
@RequestMapping("/api/recon")
public class ReconciliationController {

    private final ReconciliationEngine reconciliationEngine;
    private final ReconRunAuditRepository reconRunAuditRepository;
    private final ReconResultAuditRepository reconResultAuditRepository;
    private final com.bank.reconciliation.config.ReconProperties props;

    public ReconciliationController(ReconciliationEngine reconciliationEngine,
                                     ReconRunAuditRepository reconRunAuditRepository,
                                     ReconResultAuditRepository reconResultAuditRepository,
                                     com.bank.reconciliation.config.ReconProperties props) {
        this.reconciliationEngine = reconciliationEngine;
        this.reconRunAuditRepository = reconRunAuditRepository;
        this.reconResultAuditRepository = reconResultAuditRepository;
        this.props = props;
    }

    @PostMapping("/runs")
    public ResponseEntity<RunSummaryResponse> triggerRun(@RequestBody(required = false) RunReconciliationRequest request) {
        java.time.OffsetDateTime windowEnd = (request != null && request.windowEnd() != null)
                ? request.windowEnd()
                : java.time.OffsetDateTime.now();
        java.time.OffsetDateTime windowStart = (request != null && request.windowStart() != null)
                ? request.windowStart()
                : windowEnd.minusDays(7);

        ReconRunAudit run = reconciliationEngine.run(windowStart, windowEnd);
        List<ReconResultAudit> results = reconResultAuditRepository.findByRunId(run.getRunId());
        return ResponseEntity.ok(RunSummaryResponse.from(run, results));
    }

    @GetMapping("/runs")
    public List<RunSummaryResponse> recentRuns(
            @RequestParam(name = "includeResults", defaultValue = "true") boolean includeResults) {
        List<ReconRunAudit> runs = reconRunAuditRepository.findTop20ByOrderByRunStartedAtDesc();
        if (!includeResults) {
            return runs.stream().map(RunSummaryResponse::from).collect(Collectors.toList());
        }
        return runs.stream().map(run -> {
            List<ReconResultAudit> results = reconResultAuditRepository.findByRunId(run.getRunId());
            return RunSummaryResponse.from(run, results);
        }).collect(Collectors.toList());
    }

    @GetMapping("/runs/{runId}")
    public RunSummaryResponse getRun(@PathVariable UUID runId) {
        return reconRunAuditRepository.findById(runId)
                .map(run -> RunSummaryResponse.from(run, reconResultAuditRepository.findByRunId(runId)))
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "No such run: " + runId));
    }

    @GetMapping("/runs/{runId}/results")
    public List<ReconResultAudit> getRunResults(@PathVariable UUID runId) {
        return reconResultAuditRepository.findByRunId(runId);
    }

    /** Open breaks across all runs, newest first - the "manual review queue". */
    @GetMapping("/breaks")
    public List<ReconResultAudit> openBreaks() {
        return reconResultAuditRepository
                .findByReconStatusAndExceptionTypeIsNotNullOrderByCreatedAtDesc(ReconResultAudit.ReconStatus.EXCEPTION);
    }
}
