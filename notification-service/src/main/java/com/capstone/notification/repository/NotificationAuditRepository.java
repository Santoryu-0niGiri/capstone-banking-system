package com.capstone.notification.repository;

import com.capstone.notification.entity.NotificationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Targets NOTIFICATION_AUDIT (PostgreSQL 15+).
 * Append-only by convention — no update or delete methods declared.
 */
public interface NotificationAuditRepository extends JpaRepository<NotificationAudit, UUID> {

    List<NotificationAudit> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
