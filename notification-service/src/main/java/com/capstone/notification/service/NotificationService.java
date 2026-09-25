
package com.capstone.notification.service;

import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.notification.entity.NotificationAudit;
import com.capstone.notification.repository.NotificationAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Simulates notification dispatch (email/SMS/push) and persists every event
 * to NOTIFICATION_AUDIT (PostgreSQL) per ERD v3 — owned by notification-service.
 *
 * Swap log calls for a real provider (SES, Twilio, FCM …) for production.
 * accountId is a String UUID matching ACCOUNT_MASTER.account_id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationAuditRepository auditRepository;

    @Transactional
    public void notifyCreated(TransactionCreatedEvent event) {
        log.info("[NOTIFY] txn={} type={} amount={} account={}",
                event.txnId(), event.txnType(), event.amount(), event.accountId());

        String message = String.format(
                "Transaction %s of type %s for amount %s has been INITIATED on account %s.",
                event.txnId(), event.txnType(), event.amount(), event.accountId());

        persistAudit(event.accountId().toString(), message, "SENT");
    }

    @Transactional
    public void notifyCompleted(TransactionCompletedEvent event) {
        log.info("[NOTIFY] txn={} type={} amount={} account={} newBalance={}",
                event.txnId(), event.txnType(), event.amount(),
                event.accountId(), event.balanceAfter());

        String message = String.format(
                "Transaction %s of type %s for amount %s COMPLETED on account %s. New balance: %s.",
                event.txnId(), event.txnType(), event.amount(), event.accountId(), event.balanceAfter());

        persistAudit(event.accountId().toString(), message, "SENT");
    }

    @Transactional
    public void notifyFailed(TransactionFailedEvent event) {
        log.warn("[NOTIFY] txn={} type={} amount={} account={} FAILED reason={}",
                event.txnId(), event.txnType(), event.amount(),
                event.accountId(), event.reason());

        String message = String.format(
                "Transaction %s of type %s for amount %s FAILED on account %s. Reason: %s",
                event.txnId(), event.txnType(), event.amount(), event.accountId(), event.reason());

        persistAudit(event.accountId().toString(), message, "FAILED");
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void persistAudit(String customerId, String message, String status) {
        try {
            auditRepository.save(
                    NotificationAudit.builder()
                            .customerId(customerId)
                            .message(message)
                            .status(status)
                            .createdAt(OffsetDateTime.now())
                            .build()
            );
        } catch (Exception ex) {
            // Audit write failure must never crash the notification path itself.
            log.error("[NOTIFY] Failed to persist notification_audit for customerId={}: {}",
                    customerId, ex.getMessage(), ex);
        }
    }
}

