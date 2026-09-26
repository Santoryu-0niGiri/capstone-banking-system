package com.capstone.transaction.repository.oracle;

import com.capstone.transaction.entity.oracle.OutboxMain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Targets OUTBOX_MAIN (Oracle XE 21c).
 * Used by OutboxMainRelayService to poll and relay pending events to Kafka.
 */
public interface OutboxMainRepository extends JpaRepository<OutboxMain, String> {

    List<OutboxMain> findByStatusOrderByCreatedAtAsc(String status);
}
