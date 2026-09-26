package com.capstone.transaction.service;

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

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        List<TransactionOutbox> pending = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        
        for (TransactionOutbox entry : pending) {
            try {
                Object event = deserializeEvent(entry.getEventType(), entry.getPayload());
                eventProducer.publishRaw(entry.getAggregateId(), event);
                
                entry.setStatus("PUBLISHED");
                entry.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(entry);
                
                log.info("Successfully published outbox event {} for txn {}", entry.getEventType(), entry.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to process outbox entry {}: {}", entry.getOutboxId(), e.getMessage());
                entry.setStatus("FAILED");
                outboxRepository.save(entry);
            }
        }
    }

    private Object deserializeEvent(String eventType, String payload) throws Exception {
        return switch (eventType) {
            case "TRANSACTION_CREATED" -> objectMapper.readValue(payload, TransactionCreatedEvent.class);
            case "TRANSACTION_COMPLETED" -> objectMapper.readValue(payload, TransactionCompletedEvent.class);
            case "TRANSACTION_FAILED" -> objectMapper.readValue(payload, TransactionFailedEvent.class);
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}