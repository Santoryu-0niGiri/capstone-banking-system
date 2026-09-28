package com.bank.reconciliation.repository.postgres;

import com.bank.reconciliation.entity.postgres.ReconResultAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconResultAuditRepository extends JpaRepository<ReconResultAudit, UUID> {

    List<ReconResultAudit> findByRunId(UUID runId);

    List<ReconResultAudit> findByReconStatusAndExceptionTypeIsNotNullOrderByCreatedAtDesc(
            ReconResultAudit.ReconStatus status);

    long countByRunIdAndReconStatus(UUID runId, ReconResultAudit.ReconStatus status);
}
