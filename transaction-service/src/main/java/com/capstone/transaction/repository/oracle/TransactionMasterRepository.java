
package com.capstone.transaction.repository.oracle;

import com.capstone.transaction.entity.oracle.TransactionMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Targets TRANSACTION_MASTER via the Oracle primary DataSource.
 * String PK: txn_id (VARCHAR2 36 UUID).
 */
public interface TransactionMasterRepository extends JpaRepository<TransactionMaster, String> {

    List<TransactionMaster> findByTxnStatusAndInitiatedAtBefore(String txnStatus, LocalDateTime initiatedAt);
}

