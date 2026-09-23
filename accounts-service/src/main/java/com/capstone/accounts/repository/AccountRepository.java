package com.capstone.accounts.repository;

import com.capstone.accounts.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCustId(Long custId);
}
