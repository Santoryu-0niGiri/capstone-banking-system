package com.capstone.accounts.repository;

import com.capstone.accounts.entity.OutboxMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxMasterRepository extends JpaRepository<OutboxMaster, String> {

    Optional<OutboxMaster> findFirstBySourceServiceAndAggregateIdAndEventTypeOrderByCreatedAtAsc(
            String sourceService, String aggregateId, String eventType);

    List<OutboxMaster> findBySourceServiceAndStatusOrderByCreatedAtAsc(String sourceService, String status);

    List<OutboxMaster> findByStatusOrderByCreatedAtAsc(String status);
}
