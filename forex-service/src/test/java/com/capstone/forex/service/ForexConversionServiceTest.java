package com.capstone.forex.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.ForexConversionCompletedEvent;
import com.capstone.common.event.ForexConversionRequestedEvent;
import com.capstone.forex.entity.ForexOutbox;
import com.capstone.forex.entity.FxConversionAudit;
import com.capstone.forex.repository.ForexOutboxRepository;
import com.capstone.forex.repository.FxConversionAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexConversionServiceTest {

    @Mock
    private FxRateService fxRateService;
    @Mock
    private FxConversionAuditRepository auditRepository;
    @Mock
    private ForexOutboxRepository outboxRepository;

    private ForexConversionService conversionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        conversionService = new ForexConversionService(
                fxRateService,
                auditRepository,
                outboxRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("FC-45, FC-46: processConversion computes converted amount, persists audit and outbox row")
    void processConversion_success() {
        UUID txnId = UUID.randomUUID();
        String srcAcct = "acct-php-01";
        String destAcct = "acct-usd-02";
        BigDecimal srcAmount = new BigDecimal("5850.0000");
        BigDecimal rate = new BigDecimal("0.01709402"); // 1 / 58.50

        ForexConversionRequestedEvent request = new ForexConversionRequestedEvent(
                txnId,
                srcAcct,
                destAcct,
                "PHP",
                "USD",
                srcAmount,
                Instant.now()
        );

        when(fxRateService.getExchangeRate("PHP", "USD")).thenReturn(rate);

        ForexConversionCompletedEvent response = conversionService.processConversion(request);

        assertThat(response.txnId()).isEqualTo(txnId);
        assertThat(response.sourceAccountId()).isEqualTo(srcAcct);
        assertThat(response.destAccountId()).isEqualTo(destAcct);
        assertThat(response.sourceCurrency()).isEqualTo("PHP");
        assertThat(response.destCurrency()).isEqualTo("USD");
        assertThat(response.sourceAmount()).isEqualByComparingTo("5850.0000");
        // 5850 * 0.01709402 = 100.000017 -> 100.0000
        assertThat(response.destAmount()).isEqualByComparingTo("100.0000");
        assertThat(response.fxRate()).isEqualByComparingTo(rate);

        // Verify audit persistence
        ArgumentCaptor<FxConversionAudit> auditCaptor = ArgumentCaptor.forClass(FxConversionAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getTxnId()).isEqualTo(txnId.toString());
        assertThat(auditCaptor.getValue().getConversionStatus()).isEqualTo("COMPLETED");
        assertThat(auditCaptor.getValue().getDestAmount()).isEqualByComparingTo("100.0000");

        // Verify outbox persistence
        ArgumentCaptor<ForexOutbox> outboxCaptor = ArgumentCaptor.forClass(ForexOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getAggregateId()).isEqualTo(txnId.toString());
        assertThat(outboxCaptor.getValue().getSourceService()).isEqualTo("forex-service");
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(KafkaTopics.FOREX_CONVERSION_COMPLETED);
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo("PENDING");
    }
}
