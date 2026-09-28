package com.capstone.accounts.repository;

import com.capstone.accounts.entity.AccountMaster;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Targets ACCOUNT_MASTER via the Oracle primary DataSource.
 * String PK: account_id (VARCHAR2 36 UUID).
 */
public interface AccountRepository extends JpaRepository<AccountMaster, String> {

    List<AccountMaster> findByCustomerId(String customerId);

    /**
     * Issues SELECT … FOR UPDATE (PESSIMISTIC_WRITE) with a 5-second
     * acquisition timeout. All balance-mutation callers MUST use this
     * method — never findById — to prevent concurrent updates racing on
     * balance_amount and producing negative balances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("SELECT b FROM AccountMaster b WHERE b.accountId = :accountId")
    Optional<AccountMaster> findByIdForUpdate(@Param("accountId") String accountId);
}

