package com.capstone.transaction.saga;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.TransactionRequest;
import com.capstone.common.dto.TransactionResponse;
import com.capstone.common.event.CrossCurrencySettlementCompletedEvent;
import com.capstone.common.event.ForexConversionRequestedEvent;
import com.capstone.transaction.client.AccountsServiceClient;
import com.capstone.transaction.entity.oracle.OutboxMain;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.entity.postgres.TransactionOutbox;
import com.capstone.transaction.kafka.CrossCurrencySettlementConsumer;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.repository.oracle.OutboxMainRepository;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import com.capstone.transaction.repository.postgres.TransactionOutboxRepository;
import com.capstone.transaction.service.BalanceCacheInvalidator;
import com.capstone.transaction.service.IdempotencyService;
import com.capstone.transaction.service.OutboxMainRelayService;
import com.capstone.transaction.service.TransactionService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FC-50: End-to-end async saga loop verification for cross-currency transfers.
 * Validates the full multi-service contract across Step 1 (Initiation & Outbox),
 * Step 2 (Outbox Relay to ForEx), and Step 4 (Accounts Settlement Completion & Audit Commitment).
 */
@ExtendWith(MockitoExtension.class)
class CrossCurrencySagaAsyncTest {

    @Mock
    private AccountsServiceClient accountsServiceClient;

    @Mock
    private TransactionMasterRepository txnMasterRepository;

    @Mock
    private LedgerMutationAuditRepository auditRepository;

    @Mock
    private TransactionOutboxRepository notificationOutboxRepository;

    @Mock
    private OutboxMainRepository outboxMainRepository;

    @Mock
    private PlatformTransactionManager oracleTxManager;

    @Mock
    private PlatformTransactionManager postgresTxManager;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TransactionEventProducer eventProducer;

    @Mock
    private BalanceCacheInvalidator balanceCacheInvalidator;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private TransactionService transactionService;
    private OutboxMainRelayService outboxMainRelayService;
    private CrossCurrencySettlementConsumer settlementConsumer;

    @BeforeEach
    void setUp() {
        TransactionStatus mockStatus = mock(TransactionStatus.class);
        lenient().when(oracleTxManager.getTransaction(any())).thenReturn(mockStatus);
        lenient().when(postgresTxManager.getTransaction(any())).thenReturn(mockStatus);

        lenient().when(idempotencyService.getCached(any())).thenReturn(Optional.empty());
        lenient().when(idempotencyService.tryLock(any())).thenReturn(true);

        transactionService = new TransactionService(
                accountsServiceClient,
                txnMasterRepository,
                auditRepository,
                oracleTxManager,
                postgresTxManager,
                idempotencyService,
                eventProducer,
                balanceCacheInvalidator,
                outboxMainRepository,
                objectMapper
        );

        outboxMainRelayService = new OutboxMainRelayService(
                outboxMainRepository,
                eventProducer,
                objectMapper
        );

        settlementConsumer = new CrossCurrencySettlementConsumer(
                auditRepository,
                txnMasterRepository,
                notificationOutboxRepository,
                balanceCacheInvalidator,
                oracleTxManager,
                postgresTxManager,
                objectMapper
        );
    }

    @Test
    @DisplayName("FC-50: Complete Cross-Currency Transfer Saga: Step 1 (Initiate) -> Step 2 (Outbox Relay) -> Step 4 (Settlement & Audit)")
    void executeFullCrossCurrencyTransferSaga() throws Exception {
        UUID txnId = UUID.randomUUID();
        String sourceAccountId = "acct-usd-100";
        String destAccountId = "acct-php-200";
        BigDecimal transferAmount = new BigDecimal("200.0000"); // 200 USD
        BigDecimal fxRate = new BigDecimal("58.25000000");      // 1 USD = 58.25 PHP
        BigDecimal destAmount = new BigDecimal("11650.0000");   // 11,650 PHP
        BigDecimal srcBalanceBefore = new BigDecimal("1000.0000");
        BigDecimal destBalanceBefore = new BigDecimal("50000.0000");
        BigDecimal srcBalanceAfter = new BigDecimal("800.0000");
        BigDecimal destBalanceAfter = new BigDecimal("61650.0000");

        // Mock accounts metadata in Accounts Service
        when(accountsServiceClient.getAccount(sourceAccountId)).thenReturn(
                new AccountDTO(sourceAccountId, "cust-1", "SAVINGS", "ACTIVE", srcBalanceBefore, "USD", LocalDateTime.now())
        );
        when(accountsServiceClient.getAccount(destAccountId)).thenReturn(
                new AccountDTO(destAccountId, "cust-2", "SAVINGS", "ACTIVE", destBalanceBefore, "PHP", LocalDateTime.now())
        );

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: Client initiates cross-currency transfer via TransactionService
        // ═══════════════════════════════════════════════════════════════════════════
        TransactionRequest request = new TransactionRequest(
                sourceAccountId,
                destAccountId,
                "TRANSFER",
                transferAmount,
                "idem-saga-cross-1"
        );

        TransactionResponse response = transactionService.transfer(request, txnId);

        // Assert Step 1 outcome: Non-blocking 200 PENDING response
        assertThat(response.txnId()).isEqualTo(txnId);
        assertThat(response.txnStatus()).isEqualTo("PENDING");
        assertThat(response.isCrossCurrency()).isTrue();
        assertThat(response.targetCurrency()).isEqualTo("PHP");

        // Verify TRANSACTION_MASTER stored in Oracle as PENDING
        ArgumentCaptor<TransactionMaster> masterCaptor = ArgumentCaptor.forClass(TransactionMaster.class);
        verify(txnMasterRepository).save(masterCaptor.capture());
        TransactionMaster stagedMaster = masterCaptor.getValue();
        assertThat(stagedMaster.getTxnId()).isEqualTo(txnId.toString());
        assertThat(stagedMaster.getTxnStatus()).isEqualTo("PENDING");
        assertThat(stagedMaster.getIsCrossCurrency()).isEqualTo("Y");

        // Verify OUTBOX_MAIN stored atomically in Oracle with FOREX_CONVERSION_REQUESTED
        ArgumentCaptor<OutboxMain> outboxCaptor = ArgumentCaptor.forClass(OutboxMain.class);
        verify(outboxMainRepository).save(outboxCaptor.capture());
        OutboxMain stagedOutbox = outboxCaptor.getValue();
        assertThat(stagedOutbox.getSourceService()).isEqualTo("transaction-service");
        assertThat(stagedOutbox.getEventType()).isEqualTo(KafkaTopics.FOREX_CONVERSION_REQUESTED);
        assertThat(stagedOutbox.getStatus()).isEqualTo("PENDING");

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: OutboxMainRelayService polls OUTBOX_MAIN and relays to Kafka
        // ═══════════════════════════════════════════════════════════════════════════
        when(outboxMainRepository.findBySourceServiceAndStatusOrderByCreatedAtAsc("transaction-service", "PENDING"))
                .thenReturn(List.of(stagedOutbox));

        outboxMainRelayService.processOutbox();

        // Verify event relayed to Kafka topic FOREX_CONVERSION_REQUESTED
        ArgumentCaptor<ForexConversionRequestedEvent> forexEventCaptor =
                ArgumentCaptor.forClass(ForexConversionRequestedEvent.class);
        verify(eventProducer).publishToTopic(
                eq(KafkaTopics.FOREX_CONVERSION_REQUESTED),
                eq(txnId.toString()),
                forexEventCaptor.capture()
        );
        ForexConversionRequestedEvent dispatchedEvent = forexEventCaptor.getValue();
        assertThat(dispatchedEvent.sourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(dispatchedEvent.destAccountId()).isEqualTo(destAccountId);
        assertThat(dispatchedEvent.sourceCurrency()).isEqualTo("USD");
        assertThat(dispatchedEvent.destCurrency()).isEqualTo("PHP");
        assertThat(dispatchedEvent.sourceAmount()).isEqualByComparingTo(transferAmount);

        // Verify OUTBOX_MAIN marked PUBLISHED
        assertThat(stagedOutbox.getStatus()).isEqualTo("PUBLISHED");
        assertThat(stagedOutbox.getPublishedAt()).isNotNull();

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 3 & 4: Accounts Service settlements completes -> Transaction Service receives
        // ═══════════════════════════════════════════════════════════════════════════
        CrossCurrencySettlementCompletedEvent settlementEvent = new CrossCurrencySettlementCompletedEvent(
                txnId,
                sourceAccountId,
                destAccountId,
                "USD",
                "PHP",
                transferAmount,
                fxRate,
                destAmount,
                srcBalanceAfter,
                destBalanceAfter,
                Instant.now()
        );

        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(stagedMaster));

        settlementConsumer.onMessage(settlementEvent);

        // Verify Step 4 Postgres double-entry audit records
        ArgumentCaptor<LedgerMutationAudit> auditCaptor = ArgumentCaptor.forClass(LedgerMutationAudit.class);
        verify(auditRepository, times(2)).save(auditCaptor.capture());
        List<LedgerMutationAudit> audits = auditCaptor.getAllValues();

        LedgerMutationAudit debitAudit = audits.get(0);
        assertThat(debitAudit.getAccountId()).isEqualTo(sourceAccountId);
        assertThat(debitAudit.getMutationType()).isEqualTo("DEBIT");
        assertThat(debitAudit.getMutationAmount()).isEqualByComparingTo(transferAmount);
        assertThat(debitAudit.getAuditState()).isEqualTo("COMMITTED");

        LedgerMutationAudit creditAudit = audits.get(1);
        assertThat(creditAudit.getAccountId()).isEqualTo(destAccountId);
        assertThat(creditAudit.getMutationType()).isEqualTo("CREDIT");
        assertThat(creditAudit.getMutationAmount()).isEqualByComparingTo(destAmount);
        assertThat(creditAudit.getAuditState()).isEqualTo("COMMITTED");

        // Verify Step 4 Oracle TRANSACTION_MASTER committed
        assertThat(stagedMaster.getTxnStatus()).isEqualTo("COMMITTED");
        assertThat(stagedMaster.getDestAmount()).isEqualByComparingTo(destAmount);
        assertThat(stagedMaster.getFxRate()).isEqualByComparingTo(fxRate);

        // Verify Step 4 Redis balance caches evicted
        verify(balanceCacheInvalidator).evict(sourceAccountId);
        verify(balanceCacheInvalidator).evict(destAccountId);

        // Verify Step 4 Notification staged in Postgres outbox
        ArgumentCaptor<TransactionOutbox> notifCaptor = ArgumentCaptor.forClass(TransactionOutbox.class);
        verify(notificationOutboxRepository).save(notifCaptor.capture());
        TransactionOutbox stagedNotif = notifCaptor.getValue();
        assertThat(stagedNotif.getSourceService()).isEqualTo("transaction-service");
        assertThat(stagedNotif.getEventType()).isEqualTo("TRANSACTION_COMPLETED");
        assertThat(stagedNotif.getStatus()).isEqualTo("PENDING");
        assertThat(stagedNotif.getPayload()).contains(destAmount.toString()).contains(fxRate.toString());
    }
}
