package com.capstone.accounts.service;

import com.capstone.accounts.entity.OutboxMaster;
import com.capstone.accounts.repository.OutboxMasterRepository;
import com.capstone.common.constants.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls OUTBOX_MASTER (Oracle XE 21c) for PENDING events owned by Accounts Service
 * and relays them to Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxMasterRelayService {

    private final OutboxMasterRepository outboxMasterRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutbox() {
        List<OutboxMaster> pending = outboxMasterRepository
                .findBySourceServiceAndStatusOrderByCreatedAtAsc("accounts-service", "PENDING");

        for (OutboxMaster entry : pending) {
            try {
                String topic = resolveTopic(entry.getEventType());
                Object payloadToSend = deserializePayload(entry.getEventType(), entry.getPayload());
                kafkaTemplate.send(topic, entry.getAggregateId(), payloadToSend);

                entry.setStatus("PUBLISHED");
                entry.setPublishedAt(LocalDateTime.now());
                outboxMasterRepository.save(entry);

                log.info("OutboxMasterRelay: published event={} aggregateId={} to topic={}",
                        entry.getEventType(), entry.getAggregateId(), topic);
            } catch (Exception ex) {
                log.error("OutboxMasterRelay: failed to publish outboxId={} event={}: {}",
                        entry.getOutboxId(), entry.getEventType(), ex.getMessage());
            }
        }
    }

    private Object deserializePayload(String eventType, String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            return switch (eventType) {
                case KafkaTopics.ACCOUNT_CREATED, "ACCOUNT_CREATED" ->
                        objectMapper.readValue(payload, com.capstone.common.dto.AccountDTO.class);
                case KafkaTopics.BALANCE_UPDATED, "BALANCE_UPDATED" ->
                        objectMapper.readValue(payload, com.capstone.common.event.BalanceUpdatedEvent.class);
                case KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED, "CROSSCURRENCY_SETTLEMENT_COMPLETED" ->
                        objectMapper.readValue(payload, com.capstone.common.event.CrossCurrencySettlementCompletedEvent.class);
                default -> payload;
            };
        } catch (Exception ignored) {
            return payload;
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case KafkaTopics.ACCOUNT_CREATED, "ACCOUNT_CREATED" -> KafkaTopics.ACCOUNT_CREATED;
            case KafkaTopics.BALANCE_UPDATED, "BALANCE_UPDATED" -> KafkaTopics.BALANCE_UPDATED;
            case KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED, "CROSSCURRENCY_SETTLEMENT_COMPLETED" ->
                    KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED;
            default -> eventType;
        };
    }
}
