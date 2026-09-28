package com.capstone.forex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to OUTBOX_AUDIT (PostgreSQL 15+) for ForEx Service.
 *
 * Persisted atomically in the same local PostgreSQL transaction as FX_CONVERSION_AUDIT.
 * A background relay (ForexOutboxRelayService) polls PENDING records, publishes to Kafka,
 * and marks them PUBLISHED.
 */
@Entity
@Table(name = "outbox_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForexOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_id", updatable = false, nullable = false)
    private UUID outboxId;

    @Builder.Default
    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService = "forex-service";

    @Builder.Default
    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType = "FOREX";

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
