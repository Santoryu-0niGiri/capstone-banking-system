package com.capstone.transaction.entity.postgres;

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
 * Maps to OUTBOX_AUDIT (PostgreSQL 15+) — renamed from transaction_outbox to align with ERD v3.
 *
 * Written in the SAME local PostgreSQL transaction as the ledger_mutation_audit insert it
 * accompanies. A scheduled relay (OutboxRelayService) reads PENDING rows and publishes to Kafka,
 * then marks them PUBLISHED. This makes event publishing atomic with the audit write without
 * needing a distributed transaction across Kafka + Postgres.
 *
 * aggregate_type : e.g. "TRANSACTION", "LEDGER_MUTATION", "RECON_RESULT"
 * aggregate_id   : txn_id or mutation_uuid
 * event_type     : e.g. "transaction.completed", "ledger.mutation.posted"
 * status         : PENDING → PUBLISHED | FAILED
 */
@Entity
@Table(name = "outbox_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_id", updatable = false, nullable = false)
    private UUID outboxId;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    // Using String for JSONB payload. PostgreSQL can cast string to JSONB on insert.
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}