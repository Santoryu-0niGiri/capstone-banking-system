package com.capstone.transaction.repository.postgres;

import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerMutationAuditRepository extends JpaRepository<LedgerMutationAudit, UUID> {

    List<LedgerMutationAudit> findByAcctId(Long acctId);
}
