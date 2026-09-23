package com.capstone.transaction.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.TransactionRequest;
import com.capstone.common.dto.TransactionResponse;
import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.common.exception.IdempotencyConflictException;
import com.capstone.common.exception.InsufficientBalanceException;
import com.capstone.common.exception.LedgerPersistenceException;
import com.capstone.common.exception.ResourceNotFoundException;
import com.capstone.transaction.entity.oracle.Account;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.model.MutationResult;
import com.capstone.transaction.model.TransferResult;
import com.capstone.transaction.repository.oracle.AccountRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Core debit / credit / transfer engine.
 *
 * Concurrency: every balance read participating in a mutation is taken
 * under PESSIMISTIC_WRITE ({@link AccountRepository#findByIdForUpdate}) so
 * concurrent requests against the same account(s) serialize at the
 * database row level; a balance is validated against the requested amount
 * before it is ever written, so it can never go negative.
 *
 * Dual-write: the Oracle balance mutation and the PostgreSQL audit write
 * are two independent local transactions (see README for why true XA is
 * out of scope here). If the audit write fails, a compensating Oracle
 * transaction reverses the mutation and a LedgerPersistenceException is
 * thrown, per the spec.
 */
@Service
@Slf4j
public class TransactionService {

    private final AccountRepository accountRepository;
    private final LedgerMutationAuditRepository auditRepository;
    private final TransactionTemplate oracleTx;
    private final TransactionTemplate postgresTx;
    private final IdempotencyService idempotencyService;
    private final TransactionEventProducer eventProducer;
    private final BalanceCacheInvalidator balanceCacheInvalidator;

    public TransactionService(AccountRepository accountRepository,
                               LedgerMutationAuditRepository auditRepository,
                               @Qualifier("oracleTransactionManager") PlatformTransactionManager oracleTransactionManager,
                               @Qualifier("postgresTransactionManager") PlatformTransactionManager postgresTransactionManager,
                               IdempotencyService idempotencyService,
                               TransactionEventProducer eventProducer,
                               BalanceCacheInvalidator balanceCacheInvalidator) {
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
        this.oracleTx = new TransactionTemplate(oracleTransactionManager);
        this.postgresTx = new TransactionTemplate(postgresTransactionManager);
        this.idempotencyService = idempotencyService;
        this.eventProducer = eventProducer;
        this.balanceCacheInvalidator = balanceCacheInvalidator;
    }

    public TransactionResponse debit(TransactionRequest request) {
        return executeSingleAccount(request, "DEBIT");
    }

    public TransactionResponse credit(TransactionRequest request) {
        return executeSingleAccount(request, "CREDIT");
    }

    public TransactionResponse transfer(TransactionRequest request) {
        if (request.counterpartyAcctNo() == null) {
            throw new IllegalArgumentException("counterpartyAcctNo is required for a transfer");
        }
        if (request.counterpartyAcctNo().equals(request.acctNo())) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        return executeTransfer(request);
    }

    public Optional<LedgerMutationAudit> findAuditByTxnId(UUID txnId) {
        return auditRepository.findById(txnId);
    }

    // -------------------------------------------------------------------
    // Debit / Credit (single account)
    // -------------------------------------------------------------------

    private TransactionResponse executeSingleAccount(TransactionRequest request, String txnType) {
        Optional<TransactionResponse> cached = idempotencyService.getCached(request.idempotencyKey());
        if (cached.isPresent()) {
            return cached.get();
        }
        if (!idempotencyService.tryLock(request.idempotencyKey())) {
            throw new IdempotencyConflictException(
                    "Request with idempotency key '" + request.idempotencyKey() + "' is already being processed");
        }

        UUID txnId = UUID.randomUUID();
        eventProducer.publishCreated(new TransactionCreatedEvent(
                txnId, request.acctNo(), null, txnType, request.amount(), Instant.now()));

        try {
            BigDecimal delta = "DEBIT".equals(txnType) ? request.amount().negate() : request.amount();
            MutationResult result = applyDelta(request.acctNo(), delta);

            persistAuditOrCompensate(txnId, request.acctNo(), null, txnType, result, request.idempotencyKey());

            TransactionResponse response = new TransactionResponse(txnId, request.acctNo(), txnType,
                    request.amount(), result.balanceAfter(), "COMPLETED", Instant.now());
            idempotencyService.storeResult(request.idempotencyKey(), response);
            eventProducer.publishCompleted(new TransactionCompletedEvent(txnId, request.acctNo(), null, txnType,
                    request.amount(), result.balanceAfter(), Instant.now()));
            return response;
        } catch (RuntimeException ex) {
            idempotencyService.release(request.idempotencyKey());
            eventProducer.publishFailed(new TransactionFailedEvent(txnId, request.acctNo(), null, txnType,
                    request.amount(), ex.getMessage(), Instant.now()));
            throw ex;
        }
    }

    private MutationResult applyDelta(Long acctNo, BigDecimal delta) {
        return oracleTx.execute(status -> {
            Account account = accountRepository.findByIdForUpdate(acctNo)
                    .orElseThrow(() -> new ResourceNotFoundException("Account " + acctNo + " not found"));
            assertActive(account);

            BigDecimal before = account.getBalance();
            BigDecimal after = before.add(delta);
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientBalanceException(
                        "Account " + acctNo + " has insufficient balance for this mutation");
            }
            account.setBalance(after);
            accountRepository.save(account);
            return new MutationResult(before, after, delta);
        });
    }

    // -------------------------------------------------------------------
    // Transfer (two accounts, deterministic lock ordering to avoid deadlock)
    // -------------------------------------------------------------------

    private TransactionResponse executeTransfer(TransactionRequest request) {
        String txnType = "TRANSFER";
        Optional<TransactionResponse> cached = idempotencyService.getCached(request.idempotencyKey());
        if (cached.isPresent()) {
            return cached.get();
        }
        if (!idempotencyService.tryLock(request.idempotencyKey())) {
            throw new IdempotencyConflictException(
                    "Request with idempotency key '" + request.idempotencyKey() + "' is already being processed");
        }

        UUID txnId = UUID.randomUUID();
        eventProducer.publishCreated(new TransactionCreatedEvent(
                txnId, request.acctNo(), request.counterpartyAcctNo(), txnType, request.amount(), Instant.now()));

        try {
            TransferResult transferResult = applyTransfer(request.acctNo(), request.counterpartyAcctNo(), request.amount());
            persistTransferAuditOrCompensate(txnId, request, transferResult);

            TransactionResponse response = new TransactionResponse(txnId, request.acctNo(), txnType, request.amount(),
                    transferResult.sourceResult().balanceAfter(), "COMPLETED", Instant.now());
            idempotencyService.storeResult(request.idempotencyKey(), response);
            eventProducer.publishCompleted(new TransactionCompletedEvent(txnId, request.acctNo(),
                    request.counterpartyAcctNo(), txnType, request.amount(),
                    transferResult.sourceResult().balanceAfter(), Instant.now()));
            return response;
        } catch (RuntimeException ex) {
            idempotencyService.release(request.idempotencyKey());
            eventProducer.publishFailed(new TransactionFailedEvent(txnId, request.acctNo(),
                    request.counterpartyAcctNo(), txnType, request.amount(), ex.getMessage(), Instant.now()));
            throw ex;
        }
    }

    private TransferResult applyTransfer(Long sourceAcctNo, Long destAcctNo, BigDecimal amount) {
        Long first = Math.min(sourceAcctNo, destAcctNo);
        Long second = Math.max(sourceAcctNo, destAcctNo);

        return oracleTx.execute(status -> {
            Account firstAcct = accountRepository.findByIdForUpdate(first)
                    .orElseThrow(() -> new ResourceNotFoundException("Account " + first + " not found"));
            Account secondAcct = accountRepository.findByIdForUpdate(second)
                    .orElseThrow(() -> new ResourceNotFoundException("Account " + second + " not found"));

            Account source = first.equals(sourceAcctNo) ? firstAcct : secondAcct;
            Account dest = first.equals(sourceAcctNo) ? secondAcct : firstAcct;

            assertActive(source);
            assertActive(dest);

            BigDecimal sourceBefore = source.getBalance();
            BigDecimal sourceAfter = sourceBefore.subtract(amount);
            if (sourceAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientBalanceException(
                        "Account " + sourceAcctNo + " has insufficient balance for this transfer");
            }
            BigDecimal destBefore = dest.getBalance();
            BigDecimal destAfter = destBefore.add(amount);

            source.setBalance(sourceAfter);
            dest.setBalance(destAfter);
            accountRepository.save(source);
            accountRepository.save(dest);

            return new TransferResult(
                    new MutationResult(sourceBefore, sourceAfter, amount.negate()),
                    new MutationResult(destBefore, destAfter, amount));
        });
    }

    // -------------------------------------------------------------------
    // Dual-write: PostgreSQL audit persistence + Oracle compensation
    // -------------------------------------------------------------------

    private void persistAuditOrCompensate(UUID txnId, Long acctNo, Long counterpartyAcctNo, String txnType,
                                           MutationResult result, String idempotencyKey) {
        try {
            postgresTx.executeWithoutResult(status -> auditRepository.save(LedgerMutationAudit.builder()
                    .txnId(txnId)
                    .acctId(acctNo)
                    .mutationAmount(result.appliedDelta())
                    .txnType(txnType)
                    .counterpartyAcctId(counterpartyAcctNo)
                    .balanceAfter(result.balanceAfter())
                    .timestamp(Instant.now())
                    .auditState("RECORDED")
                    .idempotencyKey(idempotencyKey)
                    .build()));
            balanceCacheInvalidator.evict(acctNo);
        } catch (RuntimeException ex) {
            log.error("Ledger audit write failed for txn {}; compensating Oracle mutation on account {}",
                    txnId, acctNo, ex);
            compensate(acctNo, result.appliedDelta());
            throw new LedgerPersistenceException(
                    "Failed to persist ledger audit for txn " + txnId + "; balance mutation was rolled back", ex);
        }
    }

    private void persistTransferAuditOrCompensate(UUID txnId, TransactionRequest request, TransferResult result) {
        try {
            postgresTx.executeWithoutResult(status -> {
                auditRepository.save(LedgerMutationAudit.builder()
                        .txnId(txnId)
                        .acctId(request.acctNo())
                        .mutationAmount(result.sourceResult().appliedDelta())
                        .txnType("TRANSFER_OUT")
                        .counterpartyAcctId(request.counterpartyAcctNo())
                        .balanceAfter(result.sourceResult().balanceAfter())
                        .timestamp(Instant.now())
                        .auditState("RECORDED")
                        .idempotencyKey(request.idempotencyKey())
                        .build());
                auditRepository.save(LedgerMutationAudit.builder()
                        .txnId(txnId)
                        .acctId(request.counterpartyAcctNo())
                        .mutationAmount(result.destResult().appliedDelta())
                        .txnType("TRANSFER_IN")
                        .counterpartyAcctId(request.acctNo())
                        .balanceAfter(result.destResult().balanceAfter())
                        .timestamp(Instant.now())
                        .auditState("RECORDED")
                        .idempotencyKey(request.idempotencyKey())
                        .build());
            });
            balanceCacheInvalidator.evict(request.acctNo());
            balanceCacheInvalidator.evict(request.counterpartyAcctNo());
        } catch (RuntimeException ex) {
            log.error("Ledger audit write failed for transfer txn {}; compensating both legs", txnId, ex);
            compensate(request.acctNo(), result.sourceResult().appliedDelta());
            compensate(request.counterpartyAcctNo(), result.destResult().appliedDelta());
            throw new LedgerPersistenceException(
                    "Failed to persist ledger audit for transfer txn " + txnId + "; balances were rolled back", ex);
        }
    }

    /**
     * Reverses a previously-applied balance delta in a brand new Oracle
     * transaction. If this itself fails, the system is left with an
     * unaudited mutation and the failure is logged at CRITICAL severity for
     * manual reconciliation -- this is the single unavoidable edge case of
     * a non-XA dual-write and is called out explicitly in the README.
     */
    private void compensate(Long acctNo, BigDecimal appliedDelta) {
        try {
            applyDelta(acctNo, appliedDelta.negate());
            balanceCacheInvalidator.evict(acctNo);
        } catch (RuntimeException compensationException) {
            log.error("CRITICAL: compensation failed for account {} (delta {}). Manual reconciliation required.",
                    acctNo, appliedDelta, compensationException);
            throw compensationException;
        }
    }

    private void assertActive(Account account) {
        if (!"ACTIVE".equals(account.getAcctStatus())) {
            throw new IllegalStateException(
                    "Account " + account.getAcctNo() + " is not ACTIVE (status=" + account.getAcctStatus() + ")");
        }
    }
}
