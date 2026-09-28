package com.bank.reconciliation.repository.oracle;

import com.bank.reconciliation.entity.oracle.AccountMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Deliberately extends only the read slice of Spring Data - recon must
 * never persist changes into Oracle. If you need a finder, add it here;
 * do not add save()/delete() convenience methods.
 */
public interface AccountMasterRepository extends JpaRepository<AccountMaster, String> {

    List<AccountMaster> findAllByAccountIdIn(List<String> accountIds);
}
