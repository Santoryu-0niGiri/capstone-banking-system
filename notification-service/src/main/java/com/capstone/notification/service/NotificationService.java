package com.capstone.notification.service;

import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simulates a notification dispatch (email/SMS/push). In this reference
 * implementation it logs a formatted message; swap the log calls for a
 * real provider (SES, Twilio, FCM, ...) to go to production.
 */
@Service
@Slf4j
public class NotificationService {

    public void notifyCreated(TransactionCreatedEvent event) {
        log.info("[NOTIFY] Transaction {} ({}) of {} initiated on account {}",
                event.txnId(), event.txnType(), event.amount(), event.acctNo());
    }

    public void notifyCompleted(TransactionCompletedEvent event) {
        log.info("[NOTIFY] Transaction {} ({}) of {} completed on account {} -- new balance {}",
                event.txnId(), event.txnType(), event.amount(), event.acctNo(), event.balanceAfter());
    }

    public void notifyFailed(TransactionFailedEvent event) {
        log.warn("[NOTIFY] Transaction {} ({}) of {} FAILED on account {} -- reason: {}",
                event.txnId(), event.txnType(), event.amount(), event.acctNo(), event.reason());
    }
}
