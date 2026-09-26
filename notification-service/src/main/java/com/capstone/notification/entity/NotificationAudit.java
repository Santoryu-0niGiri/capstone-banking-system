package com.capstone.notification.entity;

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
 * Maps to NOTIFICATION_AUDIT (PostgreSQL 15+).
 *
 * Per ERD v3: NOTIFICATION_AUDIT is owned by notification-service.
 * One row is persisted for every transaction event consumed from Kafka,
 * regardless of whether the downstream alert dispatch (email/SMS/push) succeeded.
 *
 * status CHECK : PENDING | SENT | FAILED
 */
@Entity
@Table(name = "notification_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notif_id", updatable = false, nullable = false)
    private UUID notifId;

    /** References CUSTOMER_MASTER.customer_id (Oracle) — no cross-engine FK, indexed. */
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** PENDING → SENT | FAILED after dispatch attempt. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
