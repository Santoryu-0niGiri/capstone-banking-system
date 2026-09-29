package com.capstone.transaction.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.CrossCurrencySettlementCompletedEvent;
import com.capstone.common.event.ForexConversionRequestedEvent;
import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.transaction.entity.postgres.TransactionOutbox;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.repository.postgres.TransactionOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final TransactionOutboxRepository outboxRepository;
    private final TransactionEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000)
    @Transactional("postgresTransactionManager")
    public void processOutbox() {
        List<TransactionOutbox> pending = outboxRepository
            .findBySourceServiceAndStatusOrderByCreatedAtAsc("transaction-service", "PENDING");

        for (TransactionOutbox entry : pending) {
            try {
                Object event = deserializeEvent(entry.getEventType(), entry.getPayload());
                String topic = resolveTopic(entry.getEventType());
                eventProducer.publishToTopic(topic, entry.getAggregateId(), event);

                entry.setStatus("PUBLISHED");
                entry.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(entry);

                log.info("OutboxRelay: published event {} for aggregate {} to topic {}",
                        entry.getEventType(), entry.getAggregateId(), topic);
            } catch (Exception e) {
                log.error("OutboxRelay: failed to process outbox entry {}: {}", entry.getOutboxId(), e.getMessage());
                entry.setStatus("FAILED");
                outboxRepository.save(entry);
            }
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case KafkaTopics.FOREX_CONVERSION_REQUESTED, "FOREX_CONVERSION_REQUESTED" ->
                    KafkaTopics.FOREX_CONVERSION_REQUESTED;
            case KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED, "CROSSCURRENCY_SETTLEMENT_COMPLETED" ->
                    KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED;
            default -> KafkaTopics.TRANSACTION_EVENTS;
        };
    }

    private Object deserializeEvent(String eventType, String payload) throws Exception {
        return switch (eventType) {
            case "TRANSACTION_CREATED", "transaction.created" ->
                    objectMapper.readValue(payload, TransactionCreatedEvent.class);
            case "TRANSACTION_COMPLETED", "transaction.completed" ->
                    objectMapper.readValue(payload, TransactionCompletedEvent.class);
            case "TRANSACTION_FAILED", "transaction.failed" ->
                    objectMapper.readValue(payload, TransactionFailedEvent.class);
            case KafkaTopics.FOREX_CONVERSION_REQUESTED, "FOREX_CONVERSION_REQUESTED" ->
                    objectMapper.readValue(payload, ForexConversionRequestedEvent.class);
            case KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED, "CROSSCURRENCY_SETTLEMENT_COMPLETED" ->
                    objectMapper.readValue(payload, CrossCurrencySettlementCompletedEvent.class);
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}