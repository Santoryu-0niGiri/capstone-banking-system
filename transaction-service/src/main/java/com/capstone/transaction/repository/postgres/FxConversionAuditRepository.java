package com.capstone.transaction.repository.postgres;

import com.capstone.transaction.entity.postgres.FxConversionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FxConversionAuditRepository extends JpaRepository<FxConversionAudit, UUID> {
    List<FxConversionAudit> findByTxnId(String txnId);
}

