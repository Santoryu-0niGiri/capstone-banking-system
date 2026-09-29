package com.capstone.transaction.service;

import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrossCurrencyTimeoutServiceTest {

    @Mock
    private TransactionMasterRepository txnMasterRepository;

    @Mock
    private LedgerMutationAuditRepository auditRepository;

    @Mock
    private TransactionEventProducer eventProducer;

    @Mock
    private PlatformTransactionManager oracleTxManager;

    @Mock
    private PlatformTransactionManager postgresTxManager;

    @Mock
    private TransactionStatus transactionStatus;

    private CrossCurrencyTimeoutService timeoutService;

    @BeforeEach
    void setUp() {
        lenient().when(oracleTxManager.getTransaction(any())).thenReturn(transactionStatus);
        lenient().when(postgresTxManager.getTransaction(any())).thenReturn(transactionStatus);

        timeoutService = new CrossCurrencyTimeoutService(
                txnMasterRepository,
                auditRepository,
                eventProducer,
                oracleTxManager,
                postgresTxManager
        );
    }

    @Test
    @DisplayName("sweepTimedOutPendingTransactions marks Oracle and Postgres as ROLLED_BACK without constraint violation")
    void testSweepTimedOutTransactions_MarksRolledBack() {
        // Arrange
        TransactionMaster master = TransactionMaster.builder()
                .txnId("51d95979-3819-4ee4-956f-7419f230f8c7")
                .txnType("TRANSFER")
                .debitAccountId("acct-juan-php-01")
                .creditAccountId("acct-juan-usd-01")
                .mutationAmount(new BigDecimal("5650.0000"))
                .isCrossCurrency("Y")
                .txnStatus("PENDING")
                .initiatedAt(LocalDateTime.now().minusSeconds(20))
                .build();

        when(txnMasterRepository.findByTxnStatusAndInitiatedAtBefore(eq("PENDING"), any()))
                .thenReturn(List.of(master));

        // Act
        timeoutService.sweepTimedOutPendingTransactions();

        // Assert Oracle save
        ArgumentCaptor<TransactionMaster> masterCaptor = ArgumentCaptor.forClass(TransactionMaster.class);
        verify(txnMasterRepository).save(masterCaptor.capture());
        assertThat(masterCaptor.getValue().getTxnStatus()).isEqualTo("ROLLED_BACK");
        assertThat(masterCaptor.getValue().getUpdatedBy()).isEqualTo("TIMEOUT_SERVICE");
        assertThat(masterCaptor.getValue().getCompletedAt()).isNotNull();

        // Assert Postgres audit save
        ArgumentCaptor<LedgerMutationAudit> auditCaptor = ArgumentCaptor.forClass(LedgerMutationAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAuditState()).isEqualTo("ROLLED_BACK");
        assertThat(auditCaptor.getValue().getAccountId()).isEqualTo("acct-juan-php-01");
        assertThat(auditCaptor.getValue().getMutationAmount()).isEqualByComparingTo("5650.0000");

        // Assert Kafka event published
        ArgumentCaptor<TransactionFailedEvent> eventCaptor = ArgumentCaptor.forClass(TransactionFailedEvent.class);
        verify(eventProducer).publishFailed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().txnId().toString()).isEqualTo("51d95979-3819-4ee4-956f-7419f230f8c7");
        assertThat(eventCaptor.getValue().reason()).contains("timed out");
    }

    @Test
    @DisplayName("sweepTimedOutPendingTransactions does nothing if no timed out transactions exist")
    void testSweepTimedOutTransactions_Empty() {
        when(txnMasterRepository.findByTxnStatusAndInitiatedAtBefore(eq("PENDING"), any()))
                .thenReturn(List.of());

        timeoutService.sweepTimedOutPendingTransactions();

        verify(txnMasterRepository, never()).save(any());
        verify(auditRepository, never()).save(any());
        verify(eventProducer, never()).publishFailed(any());
    }
}
