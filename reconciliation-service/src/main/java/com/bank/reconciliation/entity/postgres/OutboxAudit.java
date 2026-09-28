package com.bank.reconciliation.entity.postgres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Matches the real outbox_audit DDL (owned by Transaction Service, its
 * Oracle twin is outbox_main): outbox_id, aggregate_type, aggregate_id,
 * event_type, payload jsonb, status, created_at, published_at.
 *
 * Recon writes a row here in the SAME local transaction as its
 * recon_run_audit/recon_result_audit inserts (see OutboxWriter /
 * ReconciliationEngine); OutboxRelay polls status='PENDING' separately
 * and publishes event_type as the Kafka topic name, aggregate_id as
 * the message key - mirroring how event_type values like
 * 'transaction.completed' are already used as topic names elsewhere
 * in the platform.
 */
@Entity
@Table(name = "outbox_audit")
@Getter
@Setter
@NoArgsConstructor
public class OutboxAudit {

    public enum Status { PENDING, PUBLISHED, FAILED }

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "outbox_id")
    private UUID outboxId;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    public static OutboxAudit of(String aggregateType, String aggregateId, String eventType, String jsonPayload) {
        OutboxAudit o = new OutboxAudit();
        o.outboxId = UUID.randomUUID();
        o.aggregateType = aggregateType;
        o.aggregateId = aggregateId;
        o.eventType = eventType;
        o.payload = jsonPayload;
        o.status = Status.PENDING;
        o.createdAt = OffsetDateTime.now();
        return o;
    }
}
