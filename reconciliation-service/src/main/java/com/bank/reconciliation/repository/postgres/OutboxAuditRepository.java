package com.bank.reconciliation.repository.postgres;

import com.bank.reconciliation.entity.postgres.OutboxAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxAuditRepository extends JpaRepository<OutboxAudit, UUID> {

    @Query("select o from OutboxAudit o where o.sourceService = 'reconciliation-service' and o.status = 'PENDING' order by o.createdAt asc")
    List<OutboxAudit> findPendingBatch(Pageable pageable);
}
