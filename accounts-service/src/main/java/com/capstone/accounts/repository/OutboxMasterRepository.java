package com.capstone.accounts.repository;

import com.capstone.accounts.entity.OutboxMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxMasterRepository extends JpaRepository<OutboxMaster, String> {

    List<OutboxMaster> findBySourceServiceAndStatusOrderByCreatedAtAsc(String sourceService, String status);

    List<OutboxMaster> findByStatusOrderByCreatedAtAsc(String status);
}
