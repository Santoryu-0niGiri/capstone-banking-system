package com.bank.reconciliation.repository.postgres;

import com.bank.reconciliation.entity.postgres.LedgerMutationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LedgerMutationAuditRepository extends JpaRepository<LedgerMutationAudit, UUID> {

    List<LedgerMutationAudit> findAllByTxnId(String txnId);

    // No posted_at column on this table - created_at is the posting time,
    // since rows are immutable (append-only trigger) from insert onward.
    @Query("select m from LedgerMutationAudit m where m.createdAt >= :windowStart and m.createdAt < :windowEnd")
    List<LedgerMutationAudit> findAllInWindow(@Param("windowStart") OffsetDateTime windowStart,
                                               @Param("windowEnd") OffsetDateTime windowEnd);
}
