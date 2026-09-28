package com.bank.reconciliation.service;

import com.bank.reconciliation.entity.postgres.OutboxAudit;
import com.bank.reconciliation.repository.postgres.OutboxAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter {

    private final OutboxAuditRepository outboxAuditRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxAuditRepository outboxAuditRepository, ObjectMapper objectMapper) {
        this.outboxAuditRepository = outboxAuditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Call this from inside the SAME @Transactional method that persists
     * the recon rows. aggregateType/aggregateId/eventType match the real
     * outbox_audit columns (e.g. aggregateType="TRANSACTION",
     * aggregateId=txnId, eventType="reconciliation.discrepancy").
     */
    public void enqueue(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxAuditRepository.save(OutboxAudit.of(aggregateType, aggregateId, eventType, json));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload for event " + eventType, e);
        }
    }
}
