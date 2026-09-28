package com.capstone.transaction.kafka;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.CrossCurrencySettlementCompletedEvent;
import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.entity.postgres.TransactionOutbox;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import com.capstone.transaction.repository.postgres.TransactionOutboxRepository;
import com.capstone.transaction.service.BalanceCacheInvalidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Consumes CROSSCURRENCY_SETTLEMENT_COMPLETED events from Kafka.
 * Appends double-entry ledger mutation audit rows in PostgreSQL
 * and marks TRANSACTION_MASTER committed in Oracle (FC-49).
 */
@Component
@Slf4j
public class CrossCurrencySettlementConsumer {

    private final LedgerMutationAuditRepository auditRepository;
    private final TransactionMasterRepository txnMasterRepository;
    private final TransactionOutboxRepository outboxRepository;
    private final BalanceCacheInvalidator balanceCacheInvalidator;
    private final TransactionTemplate oracleTx;
    private final TransactionTemplate postgresTx;
    private final ObjectMapper objectMapper;

    public CrossCurrencySettlementConsumer(
            LedgerMutationAuditRepository auditRepository,
            TransactionMasterRepository txnMasterRepository,
            TransactionOutboxRepository outboxRepository,
            BalanceCacheInvalidator balanceCacheInvalidator,
            @Qualifier("oracleTransactionManager") PlatformTransactionManager oracleTxManager,
            @Qualifier("postgresTransactionManager") PlatformTransactionManager postgresTxManager,
            ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.txnMasterRepository = txnMasterRepository;
        this.outboxRepository = outboxRepository;
        this.balanceCacheInvalidator = balanceCacheInvalidator;
        this.oracleTx = new TransactionTemplate(oracleTxManager);
        this.postgresTx = new TransactionTemplate(postgresTxManager);
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED,
            groupId = "transaction-service"
    )
    public void onMessage(Object payload) {
        if (payload == null) {
            return;
        }

        try {
            Object raw = payload instanceof org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> cr ? cr.value() : payload;
            CrossCurrencySettlementCompletedEvent event;
            if (raw instanceof CrossCurrencySettlementCompletedEvent typed) {
                event = typed;
            } else if (raw instanceof String s) {
                if (s.startsWith("\"") && s.endsWith("\"")) {
                    s = objectMapper.readValue(s, String.class);
                }
                event = objectMapper.readValue(s, CrossCurrencySettlementCompletedEvent.class);
            } else {
                event = objectMapper.convertValue(raw, CrossCurrencySettlementCompletedEvent.class);
            }

            log.info("Processing CROSSCURRENCY_SETTLEMENT_COMPLETED for txnId={}: srcAmt={} destAmt={}",
                    event.txnId(), event.sourceAmount(), event.destAmount());

            processSettlement(event);

        } catch (Exception ex) {
            log.error("Failed to process CROSSCURRENCY_SETTLEMENT_COMPLETED payload: {}", payload, ex);
        }
    }

    public void processSettlement(CrossCurrencySettlementCompletedEvent event) {
        String txnId = event.txnId().toString();

        // 1. Post double-entry audit records in PostgreSQL
        postgresTx.executeWithoutResult(status -> {
            auditRepository.save(
                    LedgerMutationAudit.builder()
                            .txnId(txnId)
                            .accountId(event.sourceAccountId())
                            .mutationAmount(event.sourceAmount())
                            .mutationType("DEBIT")
                            .txnType("TRANSFER")
                            .auditState("COMMITTED")
                            .createdAt(Instant.now())
                            .build()
            );

            auditRepository.save(
                    LedgerMutationAudit.builder()
                            .txnId(txnId)
                            .accountId(event.destAccountId())
                            .mutationAmount(event.destAmount())
                            .mutationType("CREDIT")
                            .txnType("TRANSFER")
                            .auditState("COMMITTED")
                            .createdAt(Instant.now())
                            .build()
            );

            // Queue notification outbox event
            try {
                TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                        event.txnId(),
                        event.sourceAccountId(),
                        event.destAccountId(),
                        "TRANSFER",
                        event.sourceAmount(),
                        event.sourceBalanceAfter(),
                        Instant.now(),
                        event.destCurrency(),
                        event.fxRate(),
                        event.destAmount(),
                        null,
                        true
                );

                outboxRepository.save(
                        TransactionOutbox.builder()
                                .sourceService("transaction-service")
                                .aggregateType("TRANSACTION")
                                .aggregateId(txnId)
                                .eventType("TRANSACTION_COMPLETED")
                                .payload(objectMapper.writeValueAsString(completedEvent))
                                .status("PENDING")
                                .createdAt(OffsetDateTime.now())
                                .build()
                );
            } catch (Exception ex) {
                log.warn("Failed to stage notification outbox for cross-currency txnId={}: {}", txnId, ex.getMessage());
            }
        });

        // 2. Commit transaction_master in Oracle
        oracleTx.executeWithoutResult(status -> {
            Optional<TransactionMaster> opt = txnMasterRepository.findById(txnId);
            if (opt.isPresent()) {
                TransactionMaster master = opt.get();
                master.setTxnStatus("COMMITTED");
                master.setDestAmount(event.destAmount());
                master.setFxRate(event.fxRate());
                master.setCompletedAt(LocalDateTime.now());
                master.setUpdatedAt(LocalDateTime.now());
                master.setUpdatedBy("SYSTEM");
                txnMasterRepository.save(master);
            } else {
                log.warn("TransactionMaster not found for cross-currency settlement txnId={}", txnId);
            }
        });

        // 3. Cache eviction
        balanceCacheInvalidator.evict(event.sourceAccountId());
        balanceCacheInvalidator.evict(event.destAccountId());

        log.info("Cross-currency transfer completed and committed for txnId={}", txnId);
    }
}
