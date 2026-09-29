package com.capstone.forex.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.ForexConversionCompletedEvent;
import com.capstone.common.event.ForexConversionRequestedEvent;
import com.capstone.forex.entity.ForexOutbox;
import com.capstone.forex.entity.FxConversionAudit;
import com.capstone.forex.repository.ForexOutboxRepository;
import com.capstone.forex.repository.FxConversionAuditRepository;
import com.capstone.forex.metrics.ForexMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Computes currency conversion from cached FX rates, persists FX_CONVERSION_AUDIT,
 * and writes FOREX_CONVERSION_COMPLETED to OUTBOX_AUDIT in the same Postgres transaction (FC-45, FC-46).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForexConversionService {

    private final FxRateService fxRateService;
    private final FxConversionAuditRepository auditRepository;
    private final ForexOutboxRepository outboxRepository;
    private final ForexMetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ForexConversionCompletedEvent processConversion(ForexConversionRequestedEvent request) {
        String txnId = request.txnId().toString();
        log.info("Processing ForEx conversion for txnId={}: {} {} -> {}",
                txnId, request.sourceAmount(), request.sourceCurrency(), request.destCurrency());

        try {
            BigDecimal rate = fxRateService.getExchangeRate(request.sourceCurrency(), request.destCurrency());
            BigDecimal destAmount = request.sourceAmount()
                    .multiply(rate)
                    .setScale(4, RoundingMode.HALF_UP);

            OffsetDateTime now = OffsetDateTime.now();

            // 1. Write FX conversion audit log
            FxConversionAudit audit = FxConversionAudit.builder()
                    .txnId(txnId)
                    .sourceCurrency(request.sourceCurrency().toUpperCase())
                    .destCurrency(request.destCurrency().toUpperCase())
                    .sourceAmount(request.sourceAmount())
                    .fxRate(rate)
                    .destAmount(destAmount)
                    .conversionStatus("COMPLETED")
                    .createdAt(now)
                    .build();
            auditRepository.save(audit);

            // 2. Prepare event payload
            ForexConversionCompletedEvent completedEvent = new ForexConversionCompletedEvent(
                    request.txnId(),
                    request.sourceAccountId(),
                    request.destAccountId(),
                    request.sourceCurrency().toUpperCase(),
                    request.destCurrency().toUpperCase(),
                    request.sourceAmount(),
                    rate,
                    destAmount,
                    Instant.now()
            );

            // 3. Atomically write to OUTBOX_AUDIT in the same PostgreSQL transaction
            ForexOutbox outbox = ForexOutbox.builder()
                    .sourceService("forex-service")
                    .aggregateType("FOREX")
                    .aggregateId(txnId)
                    .eventType(KafkaTopics.FOREX_CONVERSION_COMPLETED)
                    .payload(objectMapper.writeValueAsString(completedEvent))
                    .status("PENDING")
                    .createdAt(now)
                    .build();
            outboxRepository.save(outbox);

            log.info("ForEx conversion completed for txnId={}: destAmount={} (rate={})",
                    txnId, destAmount, rate);

            metricsService.recordSuccess(txnId, request.sourceCurrency(), request.destCurrency());
            return completedEvent;

        } catch (Exception ex) {
            log.error("Failed ForEx conversion for txnId={}: {}", txnId, ex.getMessage(), ex);
            metricsService.recordFailure(txnId, request.sourceCurrency(), request.destCurrency(), ex.getClass().getSimpleName());
            throw new RuntimeException("ForEx conversation failed", ex);
        } finally {
            metricsService.clearMdc();
        }
    }
}
