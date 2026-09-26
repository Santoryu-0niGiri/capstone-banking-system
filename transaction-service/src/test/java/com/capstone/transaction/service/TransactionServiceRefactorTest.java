package com.capstone.transaction.service;

import com.capstone.common.dto.AccountMutationResponse;
import com.capstone.common.dto.TransactionRequest;
import com.capstone.common.dto.TransactionResponse;
import com.capstone.common.exception.InsufficientBalanceException;
import com.capstone.common.exception.LedgerPersistenceException;
import com.capstone.common.exception.ResourceNotFoundException;
import com.capstone.transaction.client.AccountsServiceClient;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceRefactorTest {

    @Mock
    private AccountsServiceClient accountsServiceClient;

    @Mock
    private TransactionMasterRepository txnMasterRepository;

    @Mock
    private LedgerMutationAuditRepository auditRepository;

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

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        TransactionStatus mockStatus = mock(TransactionStatus.class);
        lenient().when(oracleTxManager.getTransaction(any())).thenReturn(mockStatus);
        lenient().when(postgresTxManager.getTransaction(any())).thenReturn(mockStatus);

        transactionService = new TransactionService(
                accountsServiceClient,
                txnMasterRepository,
                auditRepository,
                oracleTxManager,
                postgresTxManager,
                idempotencyService,
                eventProducer,
                balanceCacheInvalidator
        );
    }

    @Test
    @DisplayName("withdraw: succeeds, calls AccountsServiceClient debit, persists Oracle and Postgres, commits txn")
    void withdraw_success() {
        UUID txnId = UUID.randomUUID();
        String accountId = "acct-001";
        BigDecimal amount = new BigDecimal("300.0000");
        String idemKey = "idem-withdraw-1";

        TransactionRequest request = new TransactionRequest(
                accountId,
                null,
                "WITHDRAWAL",
                amount,
                idemKey
        );

        when(idempotencyService.getCached(idemKey)).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(idemKey)).thenReturn(true);

        AccountMutationResponse mutationResponse = new AccountMutationResponse(
                accountId,
                new BigDecimal("1000.0000"),
                new BigDecimal("700.0000"),
                new BigDecimal("-300.0000"),
                "PHP"
        );
        when(accountsServiceClient.debit(eq(accountId), eq(amount), eq(txnId.toString()), eq("WITHDRAWAL")))
                .thenReturn(mutationResponse);

        TransactionMaster pendingTxn = TransactionMaster.builder()
                .txnId(txnId.toString())
                .txnType("WITHDRAWAL")
                .txnStatus("PENDING")
                .build();
        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(pendingTxn));

        TransactionResponse response = transactionService.withdraw(request, txnId);

        assertThat(response.txnId()).isEqualTo(txnId);
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.txnType()).isEqualTo("WITHDRAWAL");
        assertThat(response.balanceAfter()).isEqualByComparingTo("700.0000");
        assertThat(response.txnStatus()).isEqualTo("COMMITTED");

        verify(accountsServiceClient).debit(accountId, amount, txnId.toString(), "WITHDRAWAL");

        ArgumentCaptor<LedgerMutationAudit> auditCaptor = ArgumentCaptor.forClass(LedgerMutationAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        LedgerMutationAudit savedAudit = auditCaptor.getValue();
        assertThat(savedAudit.getTxnId()).isEqualTo(txnId.toString());
        assertThat(savedAudit.getAccountId()).isEqualTo(accountId);
        assertThat(savedAudit.getMutationType()).isEqualTo("DEBIT");
        assertThat(savedAudit.getAuditState()).isEqualTo("COMMITTED");

        verify(balanceCacheInvalidator).evict(accountId);
        verify(idempotencyService).storeResult(eq(idemKey), any(TransactionResponse.class));
        verify(eventProducer).publishCompleted(any());
        assertThat(pendingTxn.getTxnStatus()).isEqualTo("COMMITTED");
    }

    @Test
    @DisplayName("deposit: succeeds, calls AccountsServiceClient credit, updates balance")
    void deposit_success() {
        UUID txnId = UUID.randomUUID();
        String accountId = "acct-002";
        BigDecimal amount = new BigDecimal("500.0000");
        String idemKey = "idem-deposit-1";

        TransactionRequest request = new TransactionRequest(
                accountId,
                null,
                "DEPOSIT",
                amount,
                idemKey
        );

        when(idempotencyService.getCached(idemKey)).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(idemKey)).thenReturn(true);

        AccountMutationResponse mutationResponse = new AccountMutationResponse(
                accountId,
                new BigDecimal("500.0000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("500.0000"),
                "PHP"
        );
        when(accountsServiceClient.credit(eq(accountId), eq(amount), eq(txnId.toString()), eq("DEPOSIT")))
                .thenReturn(mutationResponse);

        TransactionMaster pendingTxn = TransactionMaster.builder()
                .txnId(txnId.toString())
                .txnType("DEPOSIT")
                .txnStatus("PENDING")
                .build();
        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(pendingTxn));

        TransactionResponse response = transactionService.deposit(request, txnId);

        assertThat(response.balanceAfter()).isEqualByComparingTo("1000.0000");
        assertThat(response.txnStatus()).isEqualTo("COMMITTED");

        verify(accountsServiceClient).credit(accountId, amount, txnId.toString(), "DEPOSIT");
        verify(balanceCacheInvalidator).evict(accountId);
    }

    @Test
    @DisplayName("transfer: debits source, credits destination, writes 2 audit records, and commits")
    void transfer_success() {
        UUID txnId = UUID.randomUUID();
        String sourceId = "acct-src";
        String destId = "acct-dest";
        BigDecimal amount = new BigDecimal("250.0000");
        String idemKey = "idem-transfer-1";

        TransactionRequest request = new TransactionRequest(
                sourceId,
                destId,
                "TRANSFER",
                amount,
                idemKey
        );

        when(idempotencyService.getCached(idemKey)).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(idemKey)).thenReturn(true);

        AccountMutationResponse sourceResponse = new AccountMutationResponse(
                sourceId,
                new BigDecimal("1000.0000"),
                new BigDecimal("750.0000"),
                new BigDecimal("-250.0000"),
                "PHP"
        );
        AccountMutationResponse destResponse = new AccountMutationResponse(
                destId,
                new BigDecimal("300.0000"),
                new BigDecimal("550.0000"),
                new BigDecimal("250.0000"),
                "PHP"
        );

        when(accountsServiceClient.debit(eq(sourceId), eq(amount), eq(txnId.toString()), eq("TRANSFER")))
                .thenReturn(sourceResponse);
        when(accountsServiceClient.credit(eq(destId), eq(amount), eq(txnId.toString()), eq("TRANSFER")))
                .thenReturn(destResponse);

        TransactionMaster pendingTxn = TransactionMaster.builder()
                .txnId(txnId.toString())
                .txnType("TRANSFER")
                .txnStatus("PENDING")
                .build();
        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(pendingTxn));

        TransactionResponse response = transactionService.transfer(request, txnId);

        assertThat(response.txnStatus()).isEqualTo("COMMITTED");
        assertThat(response.balanceAfter()).isEqualByComparingTo("750.0000");

        verify(accountsServiceClient).debit(sourceId, amount, txnId.toString(), "TRANSFER");
        verify(accountsServiceClient).credit(destId, amount, txnId.toString(), "TRANSFER");

        verify(auditRepository, times(2)).save(any(LedgerMutationAudit.class));
        verify(balanceCacheInvalidator).evict(sourceId);
        verify(balanceCacheInvalidator).evict(destId);
        assertThat(pendingTxn.getTxnStatus()).isEqualTo("COMMITTED");
    }

    @Test
    @DisplayName("transfer: destination credit failure compensates source account and marks ROLLED_BACK")
    void transfer_destCreditFails_compensatesSourceAccount() {
        UUID txnId = UUID.randomUUID();
        String sourceId = "acct-src";
        String destId = "acct-dest";
        BigDecimal amount = new BigDecimal("250.0000");
        String idemKey = "idem-transfer-dest-fail";

        TransactionRequest request = new TransactionRequest(
                sourceId,
                destId,
                "TRANSFER",
                amount,
                idemKey
        );

        when(idempotencyService.getCached(idemKey)).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(idemKey)).thenReturn(true);

        AccountMutationResponse sourceResponse = new AccountMutationResponse(
                sourceId,
                new BigDecimal("1000.0000"),
                new BigDecimal("750.0000"),
                new BigDecimal("-250.0000"),
                "PHP"
        );
        when(accountsServiceClient.debit(eq(sourceId), eq(amount), eq(txnId.toString()), eq("TRANSFER")))
                .thenReturn(sourceResponse);
        when(accountsServiceClient.credit(eq(destId), eq(amount), eq(txnId.toString()), eq("TRANSFER")))
                .thenThrow(new ResourceNotFoundException("Destination account not found"));

        TransactionMaster pendingTxn = TransactionMaster.builder()
                .txnId(txnId.toString())
                .txnType("TRANSFER")
                .txnStatus("PENDING")
                .build();
        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(pendingTxn));

        assertThatThrownBy(() -> transactionService.transfer(request, txnId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Compensation verification: source account was credited back
        verify(accountsServiceClient).credit(eq(sourceId), eq(amount), eq(txnId.toString()), eq("TRANSFER_COMPENSATION"));

        // Status rolled back
        assertThat(pendingTxn.getTxnStatus()).isEqualTo("ROLLED_BACK");
        verify(idempotencyService).release(idemKey);
        verify(eventProducer).publishFailed(any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    @DisplayName("withdraw: insufficient balance throws exception, marks ROLLED_BACK, releases idempotency")
    void withdraw_insufficientBalance_marksRolledBack() {
        UUID txnId = UUID.randomUUID();
        String accountId = "acct-003";
        BigDecimal amount = new BigDecimal("9999.0000");
        String idemKey = "idem-insufficient";

        TransactionRequest request = new TransactionRequest(
                accountId,
                null,
                "WITHDRAWAL",
                amount,
                idemKey
        );

        when(idempotencyService.getCached(idemKey)).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(idemKey)).thenReturn(true);

        when(accountsServiceClient.debit(eq(accountId), eq(amount), eq(txnId.toString()), eq("WITHDRAWAL")))
                .thenThrow(new InsufficientBalanceException("Account acct-003 has insufficient balance"));

        TransactionMaster pendingTxn = TransactionMaster.builder()
                .txnId(txnId.toString())
                .txnType("WITHDRAWAL")
                .txnStatus("PENDING")
                .build();
        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(pendingTxn));

        assertThatThrownBy(() -> transactionService.withdraw(request, txnId))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(pendingTxn.getTxnStatus()).isEqualTo("ROLLED_BACK");
        verify(idempotencyService).release(idemKey);
        verify(eventProducer).publishFailed(any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    @DisplayName("withdraw: postgres audit failure triggers compensation to accounts service and throws LedgerPersistenceException")
    void withdraw_auditFailure_compensatesAccountsService() {
        UUID txnId = UUID.randomUUID();
        String accountId = "acct-004";
        BigDecimal amount = new BigDecimal("200.0000");
        String idemKey = "idem-audit-fail";

        TransactionRequest request = new TransactionRequest(
                accountId,
                null,
                "WITHDRAWAL",
                amount,
                idemKey
        );

        when(idempotencyService.getCached(idemKey)).thenReturn(Optional.empty());
        when(idempotencyService.tryLock(idemKey)).thenReturn(true);

        AccountMutationResponse mutationResponse = new AccountMutationResponse(
                accountId,
                new BigDecimal("1000.0000"),
                new BigDecimal("800.0000"),
                new BigDecimal("-200.0000"),
                "PHP"
        );
        when(accountsServiceClient.debit(eq(accountId), eq(amount), eq(txnId.toString()), eq("WITHDRAWAL")))
                .thenReturn(mutationResponse);

        TransactionMaster pendingTxn = TransactionMaster.builder()
                .txnId(txnId.toString())
                .txnType("WITHDRAWAL")
                .txnStatus("PENDING")
                .build();
        when(txnMasterRepository.findById(txnId.toString())).thenReturn(Optional.of(pendingTxn));

        doThrow(new RuntimeException("Postgres connection failure"))
                .when(auditRepository).save(any(LedgerMutationAudit.class));

        assertThatThrownBy(() -> transactionService.withdraw(request, txnId))
                .isInstanceOf(LedgerPersistenceException.class);

        // Verify compensation: appliedDelta was negative (-200), so credit is invoked to reverse it!
        verify(accountsServiceClient).credit(eq(accountId), eq(amount), eq(txnId.toString()), eq("COMPENSATION"));
        assertThat(pendingTxn.getTxnStatus()).isEqualTo("ROLLED_BACK");
        verify(balanceCacheInvalidator, atLeastOnce()).evict(accountId);
        verify(idempotencyService).release(idemKey);
        verify(eventProducer).publishFailed(any());
    }
}

