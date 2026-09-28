package com.capstone.transaction.saga;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.TransactionRequest;
import com.capstone.common.dto.TransactionResponse;
import com.capstone.common.event.CrossCurrencySettlementCompletedEvent;
import com.capstone.common.event.ForexConversionRequestedEvent;
import com.capstone.transaction.client.AccountsServiceClient;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.entity.postgres.TransactionOutbox;
import com.capstone.transaction.kafka.CrossCurrencySettlementConsumer;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import com.capstone.transaction.repository.postgres.TransactionOutboxRepository;
import com.capstone.transaction.service.BalanceCacheInvalidator;
import com.capstone.transaction.service.IdempotencyService;
import com.capstone.transaction.service.OutboxRelayService;
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
 * Validates the full multi-service contract across Step 1 (Initiation & Outbox in Postgres),
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
    private TransactionOutboxRepository outboxRepository;

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
    private OutboxRelayService outboxRelayService;
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
                outboxRepository,
                objectMapper
        );

        outboxRelayService = new OutboxRelayService(
                outboxRepository,
                eventProducer,
                objectMapper
        );

        settlementConsumer = new CrossCurrencySettlementConsumer(
                auditRepository,
                txnMasterRepository,
                outboxRepository,
                balanceCacheInvalidator,
                oracleTxManager,
                postgresTxManager,
                objectMapper
        );
    }

    @Test
    @DisplayName("FC-50 Full Saga: Step 1 (outbox stage) -> Step 2 (relay to Kafka) -> Step 4 (settlement commit & audit)")
    void fullCrossCurrencySagaAsyncFlow() throws Exception {
        UUID txnId = UUID.randomUUID();
        String sourceAccountId = "acct-usd-01";
        String destAccountId = "acct-php-02";
        BigDecimal transferAmount = new BigDecimal("100.0000");
        String idemKey = "cross-idem-" + txnId;

        TransactionRequest request = new TransactionRequest(
                sourceAccountId,
                destAccountId,
                "TRANSFER",
                transferAmount,
                idemKey
        );

        AccountDTO srcAcct = new AccountDTO(
                sourceAccountId, "cust-01", "CHECKING", "ACTIVE",
                new BigDecimal("500.0000"), "USD", LocalDateTime.now());

        AccountDTO destAcct = new AccountDTO(
                destAccountId, "cust-02", "SAVINGS", "ACTIVE",
                new BigDecimal("1000.0000"), "PHP", LocalDateTime.now());

        when(accountsServiceClient.getAccount(sourceAccountId)).thenReturn(srcAcct);
        when(accountsServiceClient.getAccount(destAccountId)).thenReturn(destAcct);

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: Transfer requested -> initiates cross-currency saga
        // ═══════════════════════════════════════════════════════════════════════════
        TransactionResponse initResponse = transactionService.transfer(request, txnId);

        assertThat(initResponse.txnId()).isEqualTo(txnId);
        assertThat(initResponse.txnStatus()).isEqualTo("PENDING");
        assertThat(initResponse.amount()).isEqualByComparingTo(transferAmount);

        // Verify TRANSACTION_MASTER stored in Oracle as PENDING
        ArgumentCaptor<TransactionMaster> masterCaptor = ArgumentCaptor.forClass(TransactionMaster.class);
        verify(txnMasterRepository).save(masterCaptor.capture());
        TransactionMaster stagedMaster = masterCaptor.getValue();
        assertThat(stagedMaster.getTxnId()).isEqualTo(txnId.toString());
        assertThat(stagedMaster.getTxnStatus()).isEqualTo("PENDING");
        assertThat(stagedMaster.getIsCrossCurrency()).isEqualTo("Y");

        // Verify OUTBOX_AUDIT stored atomically in PostgreSQL with FOREX_CONVERSION_REQUESTED
        ArgumentCaptor<TransactionOutbox> outboxCaptor = ArgumentCaptor.forClass(TransactionOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        TransactionOutbox stagedOutbox = outboxCaptor.getValue();
        assertThat(stagedOutbox.getSourceService()).isEqualTo("transaction-service");
        assertThat(stagedOutbox.getEventType()).isEqualTo(KafkaTopics.FOREX_CONVERSION_REQUESTED);
        assertThat(stagedOutbox.getStatus()).isEqualTo("PENDING");

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: OutboxRelayService polls OUTBOX_AUDIT and relays to Kafka
        // ═══════════════════════════════════════════════════════════════════════════
        when(outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(stagedOutbox));

        outboxRelayService.processOutbox();

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

        // Verify OUTBOX_AUDIT marked PUBLISHED
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
                new BigDecimal("58.50000000"),
                new BigDecimal("5850.0000"),
                new BigDecimal("400.0000"),
                new BigDecimal("6850.0000"),
                Instant.now()
        );

        when(txnMasterRepository.findById(txnId.toString()))
                .thenReturn(Optional.of(stagedMaster));

        // When CrossCurrencySettlementConsumer processes message
        settlementConsumer.processSettlement(settlementEvent);

        // Verify double-entry ledger mutation audit written to PostgreSQL
        ArgumentCaptor<LedgerMutationAudit> ledgerCaptor = ArgumentCaptor.forClass(LedgerMutationAudit.class);
        verify(auditRepository, times(2)).save(ledgerCaptor.capture());
        List<LedgerMutationAudit> audits = ledgerCaptor.getAllValues();

        LedgerMutationAudit debitLeg = audits.stream()
                .filter(a -> "DEBIT".equals(a.getMutationType()))
                .findFirst().orElseThrow();
        assertThat(debitLeg.getAccountId()).isEqualTo(sourceAccountId);
        assertThat(debitLeg.getMutationAmount()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(debitLeg.getAuditState()).isEqualTo("COMMITTED");

        LedgerMutationAudit creditLeg = audits.stream()
                .filter(a -> "CREDIT".equals(a.getMutationType()))
                .findFirst().orElseThrow();
        assertThat(creditLeg.getAccountId()).isEqualTo(destAccountId);
        assertThat(creditLeg.getMutationAmount()).isEqualByComparingTo(new BigDecimal("5850.0000"));
        assertThat(creditLeg.getAuditState()).isEqualTo("COMMITTED");

        // Verify TRANSACTION_MASTER updated in Oracle to COMMITTED with destAmount & fxRate
        verify(txnMasterRepository, atLeastOnce()).save(stagedMaster);
        assertThat(stagedMaster.getTxnStatus()).isEqualTo("COMMITTED");
        assertThat(stagedMaster.getDestAmount()).isEqualByComparingTo(new BigDecimal("5850.0000"));
        assertThat(stagedMaster.getFxRate()).isEqualByComparingTo(new BigDecimal("58.50000000"));
        assertThat(stagedMaster.getCompletedAt()).isNotNull();

        // Verify notification outbox entry queued in PostgreSQL
        ArgumentCaptor<TransactionOutbox> notifCaptor = ArgumentCaptor.forClass(TransactionOutbox.class);
        verify(outboxRepository, atLeast(2)).save(notifCaptor.capture());
        TransactionOutbox stagedNotif = notifCaptor.getValue();
        assertThat(stagedNotif.getSourceService()).isEqualTo("transaction-service");
        assertThat(stagedNotif.getAggregateType()).isEqualTo("TRANSACTION");
        assertThat(stagedNotif.getEventType()).isEqualTo("TRANSACTION_COMPLETED");
        assertThat(stagedNotif.getStatus()).isEqualTo("PENDING");

        // Verify cache invalidation for both accounts
        verify(balanceCacheInvalidator).evict(sourceAccountId);
        verify(balanceCacheInvalidator).evict(destAccountId);
    }
}
