package com.bank.reconciliation.repository.postgres;

import com.bank.reconciliation.entity.postgres.ReconRunAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconRunAuditRepository extends JpaRepository<ReconRunAudit, UUID> {

    List<ReconRunAudit> findTop20ByOrderByRunStartedAtDesc();
}
