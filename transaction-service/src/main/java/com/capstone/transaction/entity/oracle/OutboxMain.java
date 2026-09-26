package com.capstone.transaction.entity.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Maps to OUTBOX_MAIN (Oracle XE 21c).
 *
 * Written atomically in the SAME Oracle transaction as the TRANSACTION_MASTER and
 * ACCOUNT_MASTER writes it describes. A scheduled relay (OutboxMainRelayService)
 * polls PENDING rows, publishes the event to Kafka, then marks them PUBLISHED.
 *
 * This guarantees that if the application crashes between the DB commit and the
 * Kafka publish, the event is never silently lost — the relay will republish it
 * on the next poll cycle.
 *
 * aggregate_type : "TRANSACTION"
 * aggregate_id   : txn_id
 * event_type     : e.g. "transaction.completed", "forex.conversion.requested"
 * status         : PENDING | PUBLISHED | FAILED  (DEFAULT 'PENDING')
 */
@Entity
@Table(name = "outbox_main")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxMain {

    @Id
    @Column(name = "outbox_id", length = 36, updatable = false, nullable = false)
    private String outboxId;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    /** JSON payload stored as a CLOB-compatible String in Oracle. */
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
