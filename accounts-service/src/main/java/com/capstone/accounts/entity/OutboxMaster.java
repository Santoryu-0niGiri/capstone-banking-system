package com.capstone.accounts.entity;

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
 * Maps to OUTBOX_MASTER (Oracle XE 21c) for Accounts Service.
 *
 * Persisted atomically in the same local Oracle transaction as ACCOUNT_MASTER mutations.
 * A background relay (OutboxMasterRelayService) polls PENDING records, publishes to Kafka,
 * and marks them PUBLISHED.
 */
@Entity
@Table(name = "outbox_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxMaster {

    @Id
    @Column(name = "outbox_id", length = 36, updatable = false, nullable = false)
    private String outboxId;

    @Builder.Default
    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService = "accounts-service";

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @jakarta.persistence.Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
