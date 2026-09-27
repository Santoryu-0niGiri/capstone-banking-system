package com.bank.reconciliation.service;

import com.bank.reconciliation.config.ReconProperties;
import com.bank.reconciliation.entity.postgres.OutboxAudit;
import com.bank.reconciliation.repository.postgres.OutboxAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls outbox_audit for status='PENDING' rows and publishes them to
 * Kafka, decoupling Kafka availability from the write transaction that
 * produced recon_run_audit/recon_result_audit. event_type is used
 * directly as the Kafka topic name (e.g. "reconciliation.discrepancy"),
 * mirroring how event_type values like "transaction.completed" are
 * already used as topic names elsewhere in the platform; aggregate_id
 * is the message key.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxAuditRepository outboxAuditRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ReconProperties props;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public OutboxRelay(OutboxAuditRepository outboxAuditRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        ReconProperties props,
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.outboxAuditRepository = outboxAuditRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${recon.outbox-relay.poll-fixed-delay-ms:2000}")
    @Transactional("postgresTransactionManager")
    public void relayPendingMessages() {
        List<OutboxAudit> batch = outboxAuditRepository.findPendingBatch(
                PageRequest.of(0, props.getOutboxRelay().getBatchSize()));

        for (OutboxAudit row : batch) {
            try {
                Object payload = objectMapper.readValue(row.getPayload(), Object.class);
                kafkaTemplate.send(row.getEventType(), row.getAggregateId(), payload).get();
                row.setStatus(OutboxAudit.Status.PUBLISHED);
                row.setPublishedAt(OffsetDateTime.now());
            } catch (Exception e) {
                // Left as PENDING so the next poll retries it. A production
                // version would track attempt count and move to FAILED (plus
                // an alert) after N retries instead of retrying forever.
                log.warn("Failed to publish outbox row {} for event {} - will retry next poll",
                        row.getOutboxId(), row.getEventType(), e);
            }
            outboxAuditRepository.save(row);
        }

        if (!batch.isEmpty()) {
            log.debug("Outbox relay processed {} row(s)", batch.size());
        }
    }
}
