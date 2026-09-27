package com.bank.reconciliation.repository.oracle;

import com.bank.reconciliation.entity.oracle.TransactionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface TransactionMasterRepository extends JpaRepository<TransactionMaster, String> {

    /**
     * Windowed on initiated_at (always populated, unlike completed_at).
     * Excludes PENDING: a transaction that hasn't reached a final state
     * (COMMITTED or ROLLED_BACK) yet may simply not have its ledger leg
     * written yet, which would otherwise read as a false
     * MISSING_LEDGER_ENTRY. PENDING rows just wait for a later window.
     */
    @Query("select t from TransactionMaster t " +
            "where t.initiatedAt >= :windowStart and t.initiatedAt < :windowEnd " +
            "and t.txnStatus <> 'PENDING'")
    List<TransactionMaster> findAllInWindow(@Param("windowStart") OffsetDateTime windowStart,
                                             @Param("windowEnd") OffsetDateTime windowEnd);
}
