package com.capstone.notification.repository;

import com.capstone.notification.entity.NotificationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Targets NOTIFICATION_AUDIT (PostgreSQL 15+).
 * Append-only by convention — no update or delete methods declared.
 */
public interface NotificationAuditRepository extends JpaRepository<NotificationAudit, UUID> {

    List<NotificationAudit> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    @Query("SELECT n FROM NotificationAudit n WHERE n.customerId = :customerId OR n.customerId IN :accountIds ORDER BY n.createdAt DESC")
    List<NotificationAudit> findByCustomerIdOrAccountIds(
            @Param("customerId") String customerId,
            @Param("accountIds") Collection<String> accountIds
    );
}
