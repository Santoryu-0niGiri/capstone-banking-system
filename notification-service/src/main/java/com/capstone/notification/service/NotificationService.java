
package com.capstone.notification.service;

import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simulates notification dispatch (email/SMS/push).
 * Swap log calls for a real provider (SES, Twilio, FCM …) for production.
 * accountId is now a String UUID matching ACCOUNT_MASTER.account_id.
 */
@Service
@Slf4j
public class NotificationService {

    public void notifyCreated(TransactionCreatedEvent event) {
        log.info("[NOTIFY] txn={} type={} amount={} account={}",
                event.txnId(), event.txnType(), event.amount(), event.accountId());
    }

    public void notifyCompleted(TransactionCompletedEvent event) {
        log.info("[NOTIFY] txn={} type={} amount={} account={} newBalance={}",
                event.txnId(), event.txnType(), event.amount(),
                event.accountId(), event.balanceAfter());
    }

    public void notifyFailed(TransactionFailedEvent event) {
        log.warn("[NOTIFY] txn={} type={} amount={} account={} FAILED reason={}",
                event.txnId(), event.txnType(), event.amount(),
                event.accountId(), event.reason());
    }
}

