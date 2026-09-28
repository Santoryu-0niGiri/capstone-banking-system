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

    public ReconciliationController(ReconciliationEngine reconciliationEngine,
                                     ReconRunAuditRepository reconRunAuditRepository,
                                     ReconResultAuditRepository reconResultAuditRepository) {
        this.reconciliationEngine = reconciliationEngine;
        this.reconRunAuditRepository = reconRunAuditRepository;
        this.reconResultAuditRepository = reconResultAuditRepository;
    }

    @PostMapping("/runs")
    public ResponseEntity<RunSummaryResponse> triggerRun(@Valid @RequestBody RunReconciliationRequest request) {
        ReconRunAudit run = reconciliationEngine.run(request.windowStart(), request.windowEnd());
        return ResponseEntity.ok(RunSummaryResponse.from(run));
    }

    @GetMapping("/runs")
    public List<RunSummaryResponse> recentRuns() {
        return reconRunAuditRepository.findTop20ByOrderByRunStartedAtDesc().stream()
                .map(RunSummaryResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/runs/{runId}")
    public RunSummaryResponse getRun(@PathVariable UUID runId) {
        return reconRunAuditRepository.findById(runId)
                .map(RunSummaryResponse::from)
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
