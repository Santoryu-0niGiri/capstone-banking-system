package com.capstone.transaction.service;

import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.transaction.entity.oracle.OutboxMain;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.repository.oracle.OutboxMainRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxMainRelayServiceTest {

    @Mock
    private OutboxMainRepository outboxMainRepository;

    @Mock
    private TransactionEventProducer eventProducer;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private OutboxMainRelayService outboxMainRelayService;

    @Test
    @DisplayName("processOutbox should read PENDING rows, publish to Kafka, and mark PUBLISHED")
    void processOutbox_successfulRelay() throws Exception {
        UUID txnId = UUID.randomUUID();
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                txnId, "acct-1", null, "DEPOSIT",
                new BigDecimal("500.0000"), new BigDecimal("1500.0000"), Instant.now()
        );
        String payloadJson = objectMapper.writeValueAsString(event);

        OutboxMain pending = OutboxMain.builder()
                .outboxId("outbox-1")
                .aggregateType("TRANSACTION")
                .aggregateId(txnId.toString())
                .eventType("transaction.completed")
                .payload(payloadJson)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        when(outboxMainRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(pending));

        outboxMainRelayService.processOutbox();

        verify(eventProducer).publishRaw(eq(txnId.toString()), any(TransactionCompletedEvent.class));
        verify(outboxMainRepository).save(pending);
        org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo("PUBLISHED");
        org.assertj.core.api.Assertions.assertThat(pending.getPublishedAt()).isNotNull();
    }
}
