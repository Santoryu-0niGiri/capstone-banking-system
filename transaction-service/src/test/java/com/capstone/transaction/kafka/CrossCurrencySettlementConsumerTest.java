package com.capstone.transaction.kafka;

import com.capstone.common.event.CrossCurrencySettlementCompletedEvent;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.entity.postgres.TransactionOutbox;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import com.capstone.transaction.repository.postgres.TransactionOutboxRepository;
import com.capstone.transaction.service.BalanceCacheInvalidator;
import com.capstone.transaction.metrics.BankingMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrossCurrencySettlementConsumerTest {

    @Mock
    private LedgerMutationAuditRepository auditRepository;

    @Mock
    private TransactionMasterRepository txnMasterRepository;

    @Mock
    private TransactionOutboxRepository outboxRepository;

    @Mock
    private BalanceCacheInvalidator balanceCacheInvalidator;

    @Mock
    private BankingMetricsService metricsService;

    @Mock
    private PlatformTransactionManager oracleTxManager;

    @Mock
    private PlatformTransactionManager postgresTxManager;

    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private CrossCurrencySettlementConsumer consumer;

    @BeforeEach
    void setUp() {
        TransactionStatus mockStatus = mock(TransactionStatus.class);
        lenient().when(oracleTxManager.getTransaction(any())).thenReturn(mockStatus);
        lenient().when(postgresTxManager.getTransaction(any())).thenReturn(mockStatus);

        consumer = new CrossCurrencySettlementConsumer(
                auditRepository,
                txnMasterRepository,
                outboxRepository,
                balanceCacheInvalidator,
                metricsService,
                oracleTxManager,
                postgresTxManager,
                objectMapper
        );
    }

    @Test
    @DisplayName("processSettlement: persists double-entry audit, commits master record, stages notification, and evicts cache")
    void processSettlement_success() {
        UUID txnId = UUID.randomUUID();
        String srcAccountId = "acct-usd";
        String destAccountId = "acct-php";
        BigDecimal srcAmount = new BigDecimal("100.0000");
        BigDecimal destAmount = new BigDecimal("5800.0000");
        BigDecimal fxRate = new BigDecimal("58.00000000");
        BigDecimal srcBalanceAfter = new BigDecimal("400.0000");
        BigDecimal destBalanceAfter = new BigDecimal("30800.0000");

        CrossCurrencySettlementCompletedEvent event = new CrossCurrencySettlementCompletedEvent(
                txnId,
                srcAccountId,
                destAccountId,
                "USD",
                "PHP",
                srcAmount,
                fxRate,
                destAmount,
                srcBalanceAfter,
                destBalanceAfter,
                Instant.now()
        );

        TransactionMaster pendingMaster = TransactionMaster.builder()
                .txnId(txnId.toString())
                .txnType("TRANSFER")
                .txnStatus("PENDING")
                .isCrossCurrency("Y")
                .build();

        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(pendingMaster));

        consumer.onMessage(event);

        // Verify double-entry audit rows
        ArgumentCaptor<LedgerMutationAudit> auditCaptor = ArgumentCaptor.forClass(LedgerMutationAudit.class);
        verify(auditRepository, times(2)).save(auditCaptor.capture());
        List<LedgerMutationAudit> savedAudits = auditCaptor.getAllValues();

        LedgerMutationAudit debitAudit = savedAudits.get(0);
        assertThat(debitAudit.getTxnId()).isEqualTo(txnId.toString());
        assertThat(debitAudit.getAccountId()).isEqualTo(srcAccountId);
        assertThat(debitAudit.getMutationType()).isEqualTo("DEBIT");
        assertThat(debitAudit.getMutationAmount()).isEqualByComparingTo(srcAmount);
        assertThat(debitAudit.getCurrencyCode()).isEqualTo("USD");
        assertThat(debitAudit.getAuditState()).isEqualTo("COMMITTED");

        LedgerMutationAudit creditAudit = savedAudits.get(1);
        assertThat(creditAudit.getTxnId()).isEqualTo(txnId.toString());
        assertThat(creditAudit.getAccountId()).isEqualTo(destAccountId);
        assertThat(creditAudit.getMutationType()).isEqualTo("CREDIT");
        assertThat(creditAudit.getMutationAmount()).isEqualByComparingTo(destAmount);
        assertThat(creditAudit.getCurrencyCode()).isEqualTo("PHP");
        assertThat(creditAudit.getAuditState()).isEqualTo("COMMITTED");

        // Verify notification outbox record
        ArgumentCaptor<TransactionOutbox> outboxCaptor = ArgumentCaptor.forClass(TransactionOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        TransactionOutbox savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getSourceService()).isEqualTo("transaction-service");
        assertThat(savedOutbox.getEventType()).isEqualTo("TRANSACTION_COMPLETED");
        assertThat(savedOutbox.getAggregateId()).isEqualTo(txnId.toString());
        assertThat(savedOutbox.getStatus()).isEqualTo("PENDING");

        // Verify TransactionMaster committed
        verify(txnMasterRepository).save(pendingMaster);
        assertThat(pendingMaster.getTxnStatus()).isEqualTo("COMMITTED");
        assertThat(pendingMaster.getDestAmount()).isEqualByComparingTo(destAmount);
        assertThat(pendingMaster.getFxRate()).isEqualByComparingTo(fxRate);
        assertThat(pendingMaster.getCompletedAt()).isNotNull();

        // Verify Redis cache eviction for both accounts
        verify(balanceCacheInvalidator).evict(srcAccountId);
        verify(balanceCacheInvalidator).evict(destAccountId);
    }

    @Test
    @DisplayName("processSettlement: duplicate event does not append audits or another notification")
    void processSettlement_duplicateEvent_isIgnored() {
        UUID txnId = UUID.randomUUID();
        String txnIdString = txnId.toString();
        CrossCurrencySettlementCompletedEvent event = new CrossCurrencySettlementCompletedEvent(
                txnId,
                "acct-usd",
                "acct-php",
                "USD",
                "PHP",
                new BigDecimal("100.0000"),
                new BigDecimal("58.00000000"),
                new BigDecimal("5800.0000"),
                new BigDecimal("400.0000"),
                new BigDecimal("30800.0000"),
                Instant.now()
        );
        TransactionMaster pendingMaster = TransactionMaster.builder()
                .txnId(txnIdString)
                .txnType("TRANSFER")
                .txnStatus("PENDING")
                .isCrossCurrency("Y")
                .build();
        List<LedgerMutationAudit> existingAudits = List.of(
                LedgerMutationAudit.builder().txnId(txnIdString).mutationType("DEBIT").build(),
                LedgerMutationAudit.builder().txnId(txnIdString).mutationType("CREDIT").build()
        );

        when(auditRepository.findByTxnId(txnIdString)).thenReturn(existingAudits);
        when(txnMasterRepository.findById(txnIdString)).thenReturn(Optional.of(pendingMaster));

        consumer.processSettlement(event);

        verify(auditRepository, never()).save(any(LedgerMutationAudit.class));
        verify(outboxRepository, never()).save(any(TransactionOutbox.class));
        verify(txnMasterRepository).save(pendingMaster);
        assertThat(pendingMaster.getTxnStatus()).isEqualTo("COMMITTED");
        verify(balanceCacheInvalidator).evict("acct-usd");
        verify(balanceCacheInvalidator).evict("acct-php");
    }
}
