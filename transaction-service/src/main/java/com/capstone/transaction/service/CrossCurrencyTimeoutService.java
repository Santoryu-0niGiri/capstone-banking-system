package com.capstone.transaction.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sweeps for cross-currency transfers stuck in PENDING status when the downstream
 * ForEx Service is offline or fails to respond.
 *
 * Transitions the transaction to ROLLED_BACK in Oracle and PostgreSQL,
 * and publishes TransactionFailedEvent to Kafka so Notification Service alerts the customer.
 */
@Service
@Slf4j
public class CrossCurrencyTimeoutService {

    private final TransactionMasterRepository txnMasterRepository;
    private final LedgerMutationAuditRepository auditRepository;
    private final TransactionEventProducer eventProducer;
    private final TransactionTemplate oracleTx;
    private final TransactionTemplate postgresTx;

    public CrossCurrencyTimeoutService(
            TransactionMasterRepository txnMasterRepository,
            LedgerMutationAuditRepository auditRepository,
            TransactionEventProducer eventProducer,
            @Qualifier("oracleTransactionManager") PlatformTransactionManager oracleTxManager,
            @Qualifier("postgresTransactionManager") PlatformTransactionManager postgresTxManager) {
        this.txnMasterRepository = txnMasterRepository;
        this.auditRepository = auditRepository;
        this.eventProducer = eventProducer;
        this.oracleTx = new TransactionTemplate(oracleTxManager);
        this.postgresTx = new TransactionTemplate(postgresTxManager);
    }

    @Scheduled(fixedDelay = 5000)
    public void sweepTimedOutPendingTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(15);
        List<TransactionMaster> timedOut = txnMasterRepository.findByTxnStatusAndInitiatedAtBefore("PENDING", threshold);

        if (timedOut.isEmpty()) {
            return;
        }

        log.info("CrossCurrencyTimeoutService: Found {} pending transactions exceeding 15s timeout", timedOut.size());

        for (TransactionMaster master : timedOut) {
            String txnId = master.getTxnId();
            try {
                // 1. Mark ROLLED_BACK in Oracle TRANSACTION_MASTER (satisfies ck_txn_status)
                oracleTx.executeWithoutResult(status -> {
                    master.setTxnStatus("ROLLED_BACK");
                    master.setCompletedAt(LocalDateTime.now());
                    master.setUpdatedAt(LocalDateTime.now());
                    master.setUpdatedBy("TIMEOUT_SERVICE");
                    txnMasterRepository.save(master);
                });

                // 2. Persist ROLLED_BACK audit record in PostgreSQL (satisfies ck_audit_state)
                postgresTx.executeWithoutResult(status -> {
                    auditRepository.save(
                            LedgerMutationAudit.builder()
                                    .txnId(txnId)
                                    .accountId(master.getDebitAccountId())
                                    .mutationAmount(master.getMutationAmount() != null ? master.getMutationAmount() : BigDecimal.ZERO)
                                    .mutationType("DEBIT")
                                    .txnType(master.getTxnType() != null ? master.getTxnType() : "TRANSFER")
                                    .auditState("ROLLED_BACK")
                                    .createdAt(Instant.now())
                                    .build()
                    );
                });

                // 3. Publish TransactionFailedEvent to Kafka for Notification Service
                UUID uuid;
                try {
                    uuid = UUID.fromString(txnId);
                } catch (Exception e) {
                    uuid = UUID.randomUUID();
                }

                TransactionFailedEvent failedEvent = new TransactionFailedEvent(
                        uuid,
                        master.getDebitAccountId(),
                        master.getCreditAccountId(),
                        master.getTxnType() != null ? master.getTxnType() : "TRANSFER",
                        master.getMutationAmount() != null ? master.getMutationAmount() : BigDecimal.ZERO,
                        "Cross-currency settlement timed out: ForEx service is unavailable",
                        Instant.now()
                );

                eventProducer.publishFailed(failedEvent);

                log.warn("Marked cross-currency txnId={} as ROLLED_BACK due to ForEx service timeout; notified customer", txnId);

            } catch (Exception ex) {
                log.error("Failed to process timeout for txnId={}: {}", txnId, ex.getMessage(), ex);
            }
        }
    }
}
