package com.capstone.forex.service;

import com.capstone.forex.entity.ForexOutbox;
import com.capstone.forex.repository.ForexOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls OUTBOX_AUDIT (PostgreSQL 15+) for PENDING events owned by ForEx Service
 * and relays them to Kafka topic FOREX_CONVERSION_COMPLETED (FC-46).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForexOutboxRelayService {

    private final ForexOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutbox() {
        List<ForexOutbox> pending = outboxRepository
                .findBySourceServiceAndStatusOrderByCreatedAtAsc("forex-service", "PENDING");

        for (ForexOutbox entry : pending) {
            try {
                kafkaTemplate.send(entry.getEventType(), entry.getAggregateId(), entry.getPayload());

                entry.setStatus("PUBLISHED");
                entry.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(entry);

                log.info("ForexOutboxRelay: published event={} aggregateId={} outboxId={}",
                        entry.getEventType(), entry.getAggregateId(), entry.getOutboxId());
            } catch (Exception ex) {
                log.error("ForexOutboxRelay: failed to publish outboxId={} event={}: {}",
                        entry.getOutboxId(), entry.getEventType(), ex.getMessage());
            }
        }
    }
}
