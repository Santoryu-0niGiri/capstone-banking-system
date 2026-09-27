package com.capstone.transaction.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.CrossCurrencySettlementCompletedEvent;
import com.capstone.common.event.ForexConversionRequestedEvent;
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
 * Polls OUTBOX_MAIN (Oracle XE 21c) for PENDING events owned by Transaction Service
 * and relays them to Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxMainRelayService {

    private final OutboxMainRepository outboxMainRepository;
    private final TransactionEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000)
    @Transactional("oracleTransactionManager")
    public void processOutbox() {
        List<OutboxMain> pending = outboxMainRepository
                .findBySourceServiceAndStatusOrderByCreatedAtAsc("transaction-service", "PENDING");

        for (OutboxMain entry : pending) {
            try {
                Object event = deserializeEvent(entry.getEventType(), entry.getPayload());
                String topic = resolveTopic(entry.getEventType());

                eventProducer.publishToTopic(topic, entry.getAggregateId(), event);

                entry.setStatus("PUBLISHED");
                entry.setPublishedAt(LocalDateTime.now());
                outboxMainRepository.save(entry);

                log.info("OutboxMain: published event={} aggregateId={} to topic={}",
                        entry.getEventType(), entry.getAggregateId(), topic);

            } catch (Exception e) {
                log.error("OutboxMain: failed to relay outboxId={} event={}: {}",
                        entry.getOutboxId(), entry.getEventType(), e.getMessage());
                entry.setStatus("FAILED");
                outboxMainRepository.save(entry);
            }
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case KafkaTopics.FOREX_CONVERSION_REQUESTED, "FOREX_CONVERSION_REQUESTED" ->
                    KafkaTopics.FOREX_CONVERSION_REQUESTED;
            case KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED, "CROSSCURRENCY_SETTLEMENT_COMPLETED" ->
                    KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED;
            case KafkaTopics.ACCOUNT_CREATED, "ACCOUNT_CREATED" ->
                    KafkaTopics.ACCOUNT_CREATED;
            case KafkaTopics.BALANCE_UPDATED, "BALANCE_UPDATED" ->
                    KafkaTopics.BALANCE_UPDATED;
            default -> KafkaTopics.TRANSACTION_EVENTS;
        };
    }

    private Object deserializeEvent(String eventType, String payload) throws Exception {
        return switch (eventType) {
            case "transaction.created", "TRANSACTION_CREATED" ->
                    objectMapper.readValue(payload, TransactionCreatedEvent.class);
            case "transaction.completed", "TRANSACTION_COMPLETED" ->
                    objectMapper.readValue(payload, TransactionCompletedEvent.class);
            case "transaction.failed", "TRANSACTION_FAILED" ->
                    objectMapper.readValue(payload, TransactionFailedEvent.class);
            case KafkaTopics.FOREX_CONVERSION_REQUESTED, "FOREX_CONVERSION_REQUESTED" ->
                    objectMapper.readValue(payload, ForexConversionRequestedEvent.class);
            case KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED, "CROSSCURRENCY_SETTLEMENT_COMPLETED" ->
                    objectMapper.readValue(payload, CrossCurrencySettlementCompletedEvent.class);
            default -> throw new IllegalArgumentException(
                    "OutboxMain: unknown event type: " + eventType);
        };
    }
}
