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
 * Polls OUTBOX_MAIN (Oracle XE 21c) for PENDING events owned by Accounts Service
 * and relays them to Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxMasterRelayService {

    private final OutboxMasterRepository outboxMasterRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutbox() {
        List<OutboxMaster> pending = outboxMasterRepository
                .findBySourceServiceAndStatusOrderByCreatedAtAsc("accounts-service", "PENDING");

        for (OutboxMaster entry : pending) {
            try {
                String topic = resolveTopic(entry.getEventType());
                kafkaTemplate.send(topic, entry.getAggregateId(), entry.getPayload());

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
