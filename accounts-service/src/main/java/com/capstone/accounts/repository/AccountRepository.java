package com.capstone.accounts.repository;

import com.capstone.accounts.entity.CustomerBalanceMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Targets CUSTOMER_BALANCE_MASTER via the Oracle primary DataSource.
 * String PK: account_id (VARCHAR2 36 UUID).
 */
public interface AccountRepository extends JpaRepository<CustomerBalanceMaster, String> {

    List<CustomerBalanceMaster> findByCustomerId(String customerId);
}

