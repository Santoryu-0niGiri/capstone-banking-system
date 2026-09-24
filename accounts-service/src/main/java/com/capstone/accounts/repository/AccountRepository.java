package com.capstone.accounts.repository;

import com.capstone.accounts.entity.AccountMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Targets ACCOUNT_MASTER via the Oracle primary DataSource.
 * String PK: account_id (VARCHAR2 36 UUID).
 */
public interface AccountRepository extends JpaRepository<AccountMaster, String> {

    List<AccountMaster> findByCustomerId(String customerId);
}

