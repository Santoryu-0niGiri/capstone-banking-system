package com.capstone.transaction.repository.oracle;

import com.capstone.transaction.entity.oracle.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Acquires a `SELECT ... FOR UPDATE` row lock (PESSIMISTIC_WRITE) on the
     * account row so concurrent debit/credit/transfer requests against the
     * same account serialize instead of racing on the balance column. A
     * 5-second lock-acquisition timeout prevents indefinite blocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("select a from Account a where a.acctNo = :acctNo")
    Optional<Account> findByIdForUpdate(@Param("acctNo") Long acctNo);
}
