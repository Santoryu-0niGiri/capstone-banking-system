package com.capstone.transaction.service;

import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.transaction.entity.oracle.OutboxMain;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.repository.oracle.OutboxMainRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls OUTBOX_MAIN (Oracle XE 21c) for PENDING events and relays them to Kafka.
 *
 * This service completes the Transactional Outbox pattern for the Oracle side:
 * - TRANSACTION_MASTER and ACCOUNT_MASTER writes happen atomically with an OUTBOX_MAIN insert.
 * - This relay reads PENDING rows, publishes to Kafka, then marks them PUBLISHED.
 * - If Kafka is unavailable, rows stay PENDING and are retried on the next poll cycle.
 *
 * Runs every 5 seconds (same cadence as OutboxRelayService for the Postgres side).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxMainRelayService {

    private final OutboxMainRepository outboxMainRepository;
    private final TransactionEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional("oracleTransactionManager")
    public void processOutbox() {
        List<OutboxMain> pending = outboxMainRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxMain entry : pending) {
            try {
                Object event = deserializeEvent(entry.getEventType(), entry.getPayload());
                eventProducer.publishRaw(entry.getAggregateId(), event);

                entry.setStatus("PUBLISHED");
                entry.setPublishedAt(LocalDateTime.now());
                outboxMainRepository.save(entry);

                log.info("OutboxMain: published event={} aggregateId={} outboxId={}",
                        entry.getEventType(), entry.getAggregateId(), entry.getOutboxId());

            } catch (Exception e) {
                log.error("OutboxMain: failed to relay outboxId={} event={}: {}",
                        entry.getOutboxId(), entry.getEventType(), e.getMessage());
                entry.setStatus("FAILED");
                outboxMainRepository.save(entry);
            }
        }
    }

    private Object deserializeEvent(String eventType, String payload) throws Exception {
        return switch (eventType) {
            case "transaction.created",  "TRANSACTION_CREATED"   ->
                    objectMapper.readValue(payload, TransactionCreatedEvent.class);
            case "transaction.completed", "TRANSACTION_COMPLETED" ->
                    objectMapper.readValue(payload, TransactionCompletedEvent.class);
            case "transaction.failed",  "TRANSACTION_FAILED"    ->
                    objectMapper.readValue(payload, TransactionFailedEvent.class);
            default -> throw new IllegalArgumentException(
                    "OutboxMain: unknown event type: " + eventType);
        };
    }
}
