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
import com.capstone.transaction.entity.oracle.AccountMaster;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.model.MutationResult;
import com.capstone.transaction.model.TransferResult;
import com.capstone.transaction.repository.oracle.AccountRepository;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core balance mutation engine: WITHDRAWAL, DEPOSIT, TRANSFER.
 *
 * ── Concurrency ──────────────────────────────────────────────────────────────
 * Every balance read that feeds a mutation uses PESSIMISTIC_WRITE
 * (AccountRepository#findByIdForUpdate → SELECT … FOR UPDATE with a 5-second
 * timeout). Concurrent requests against the same account(s) serialize at the
 * Oracle row level; balance_amount can never go below 0.
 *
 * ── Three-phase write per transaction ────────────────────────────────────────
 * Phase 1 (Oracle): Acquire row lock, validate balance, write
 *          ACCOUNT_MASTER + TRANSACTION_MASTER (txn_status=PENDING).
 * Phase 2 (PostgreSQL): Append double-entry rows to ledger_mutation_audit.
 * Phase 3 (Oracle): Update TRANSACTION_MASTER txn_status → COMMITTED.
 *
 * If Phase 2 fails → compensating Oracle transaction reverses the balance
 * delta AND sets txn_status=ROLLED_BACK, then throws LedgerPersistenceException.
 */
@Service
@Slf4j
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionMasterRepository txnMasterRepository;
    private final LedgerMutationAuditRepository auditRepository;
    private final TransactionTemplate oracleTx;
    private final TransactionTemplate postgresTx;
    private final IdempotencyService idempotencyService;
    private final TransactionEventProducer eventProducer;
    private final BalanceCacheInvalidator balanceCacheInvalidator;

    public TransactionService(
            AccountRepository accountRepository,
            TransactionMasterRepository txnMasterRepository,
            LedgerMutationAuditRepository auditRepository,
            @Qualifier("oracleTransactionManager")
            PlatformTransactionManager oracleTxManager,
            @Qualifier("postgresTransactionManager")
            PlatformTransactionManager postgresTxManager,
            IdempotencyService idempotencyService,
            TransactionEventProducer eventProducer,
            BalanceCacheInvalidator balanceCacheInvalidator) {

        this.accountRepository = accountRepository;
        this.txnMasterRepository = txnMasterRepository;
        this.auditRepository = auditRepository;
        this.oracleTx = new TransactionTemplate(oracleTxManager);
        this.postgresTx = new TransactionTemplate(postgresTxManager);
        this.idempotencyService = idempotencyService;
        this.eventProducer = eventProducer;
        this.balanceCacheInvalidator = balanceCacheInvalidator;
    }

    // ── Public API ────────────────────────────────────────────────────────────

  public TransactionResponse withdraw(
        TransactionRequest request,
        UUID txnId) {

    return executeSingleLeg(
            request,
            "WITHDRAWAL",
            "DEBIT",
            txnId.toString());
}

public TransactionResponse deposit(
        TransactionRequest request,
        UUID txnId) {

    return executeSingleLeg(
            request,
            "DEPOSIT",
            "CREDIT",
            txnId.toString());
}

    public TransactionResponse transfer(
        TransactionRequest request,
        UUID txnId) {

        if (request.counterpartyAccountId() == null
                || request.counterpartyAccountId().isBlank()) {

            throw new IllegalArgumentException(
                    "counterpartyAccountId is required for TRANSFER");
        }

        if (request.counterpartyAccountId().equals(request.accountId())) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must differ");
        }

        return executeTransfer(
        request,
        txnId.toString());
    }

    /**
     * Finds all PostgreSQL audit records belonging to a transaction.
     *
     * txn_id is stored as a VARCHAR/character column in PostgreSQL and is
     * represented as String in LedgerMutationAudit.
     *
     * The UUID validation is performed in Java so invalid transaction IDs
     * are rejected before reaching the database.
     */
    public List<LedgerMutationAudit> findAuditByTxnId(String txnId) {

        if (txnId == null || txnId.isBlank()) {
            throw new IllegalArgumentException(
                    "Transaction ID is required");
        }

        try {
            UUID.fromString(txnId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid transaction ID: " + txnId, ex);
        }

        return auditRepository.findByTxnId(txnId);
    }

    // ── Single-leg (WITHDRAWAL / DEPOSIT) ─────────────────────────────────────

    private TransactionResponse executeSingleLeg(
        TransactionRequest request,
        String txnType,
        String mutationType,
        String txnId) {

        Optional<TransactionResponse> cached =
                idempotencyService.getCached(request.idempotencyKey());

        if (cached.isPresent()) {
            return cached.get();
        }

        if (!idempotencyService.tryLock(request.idempotencyKey())) {
            throw new IdempotencyConflictException(
                    "Request with idempotency key '"
                            + request.idempotencyKey()
                            + "' is already being processed");
        }


        try {

            // Phase 1: Oracle — balance mutation +
            // TRANSACTION_MASTER PENDING
            BigDecimal delta = "DEBIT".equals(mutationType)
                    ? request.amount().negate()
                    : request.amount();

            MutationResult result = applyDelta(
                    request.accountId(),
                    delta,
                    txnId,
                    txnType,
                    "DEBIT".equals(mutationType)
                            ? request.accountId()
                            : null,
                    "CREDIT".equals(mutationType)
                            ? request.accountId()
                            : null);

            eventProducer.publishCreated(
                    new TransactionCreatedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            null,
                            txnType,
                            request.amount(),
                            Instant.now()));

            // Phase 2 + 3:
            // PostgreSQL audit + Oracle status COMMITTED
            persistAuditOrCompensate(
                    txnId,
                    request.accountId(),
                    txnType,
                    mutationType,
                    request.amount(),
                    result);

            TransactionResponse response =
                    new TransactionResponse(
                            UUID.fromString(txnId),
                            request.accountId(),
                            txnType,
                            request.amount(),
                            result.balanceAfter(),
                            "COMMITTED",
                            Instant.now());

            idempotencyService.storeResult(
                    request.idempotencyKey(),
                    response);

            eventProducer.publishCompleted(
                    new TransactionCompletedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            null,
                            txnType,
                            request.amount(),
                            result.balanceAfter(),
                            Instant.now()));

            return response;

        } catch (RuntimeException ex) {

            idempotencyService.release(
                    request.idempotencyKey());

            eventProducer.publishFailed(
                    new TransactionFailedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            null,
                            txnType,
                            request.amount(),
                            ex.getMessage(),
                            Instant.now()));

            throw ex;
        }
    }

    // ── Transfer ─────────────────────────────────────────────────────────────

    private TransactionResponse executeTransfer(
        TransactionRequest request,
        String txnId) {

        Optional<TransactionResponse> cached =
                idempotencyService.getCached(
                        request.idempotencyKey());

        if (cached.isPresent()) {
            return cached.get();
        }

        if (!idempotencyService.tryLock(
                request.idempotencyKey())) {

            throw new IdempotencyConflictException(
                    "Request with idempotency key '"
                            + request.idempotencyKey()
                            + "' is already being processed");
        }

       

        try {

            // Phase 1:
            // Oracle — debit source + credit destination
            TransferResult transferResult =
                    applyTransfer(
                            request.accountId(),
                            request.counterpartyAccountId(),
                            request.amount(),
                            txnId);

            eventProducer.publishCreated(
                    new TransactionCreatedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            request.counterpartyAccountId(),
                            "TRANSFER",
                            request.amount(),
                            Instant.now()));

            // Phase 2 + 3:
            // PostgreSQL audit + Oracle status COMMITTED
            persistTransferAuditOrCompensate(
                    txnId,
                    request,
                    transferResult);

            TransactionResponse response =
                    new TransactionResponse(
                            UUID.fromString(txnId),
                            request.accountId(),
                            "TRANSFER",
                            request.amount(),
                            transferResult
                                    .sourceResult()
                                    .balanceAfter(),
                            "COMMITTED",
                            Instant.now());

            idempotencyService.storeResult(
                    request.idempotencyKey(),
                    response);

            eventProducer.publishCompleted(
                    new TransactionCompletedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            request.counterpartyAccountId(),
                            "TRANSFER",
                            request.amount(),
                            transferResult
                                    .sourceResult()
                                    .balanceAfter(),
                            Instant.now()));

            return response;

        } catch (RuntimeException ex) {

            idempotencyService.release(
                    request.idempotencyKey());

            eventProducer.publishFailed(
                    new TransactionFailedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            request.counterpartyAccountId(),
                            "TRANSFER",
                            request.amount(),
                            ex.getMessage(),
                            Instant.now()));

            throw ex;
        }
    }

    // ── Oracle Phase 1 ────────────────────────────────────────────────────────

    private MutationResult applyDelta(
            String accountId,
            BigDecimal delta,
            String txnId,
            String txnType,
            String debitAccountId,
            String creditAccountId) {

        MutationResult result =
                oracleTx.execute(status -> {

                    AccountMaster account =
                            accountRepository
                                    .findByIdForUpdate(accountId)
                                    .orElseThrow(() ->
                                            new ResourceNotFoundException(
                                                    "Account "
                                                            + accountId
                                                            + " not found"));

                    assertActive(account);

                    BigDecimal before =
                            account.getBalanceAmount();

                    BigDecimal after =
                            before.add(delta);

                    if (after.compareTo(
                            BigDecimal.ZERO) < 0) {

                        throw new InsufficientBalanceException(
                                "Account "
                                        + accountId
                                        + " has insufficient balance");
                    }

                    LocalDateTime now =
                            LocalDateTime.now();

                    account.setBalanceAmount(after);
                    account.setUpdatedAt(now);
                    account.setUpdatedBy("SYSTEM");

                    accountRepository.save(account);

                    TransactionMaster txnMaster =
                            TransactionMaster.builder()
                                    .txnId(txnId)
                                    .txnType(txnType)
                                    .debitAccountId(
                                            debitAccountId)
                                    .creditAccountId(
                                            creditAccountId)
                                    .mutationAmount(
                                            delta.abs())
                                    .txnStatus("PENDING")
                                    .initiatedAt(now)
                                    .createdAt(now)
                                    .createdBy("SYSTEM")
                                    .build();

                    txnMasterRepository.save(txnMaster);

                    return new MutationResult(
                            before,
                            after,
                            delta);
                });

        if (result == null) {
            throw new IllegalStateException(
                    "Oracle transaction for account "
                            + accountId
                            + " returned no result");
        }

        return result;
    }

    private TransferResult applyTransfer(
            String sourceId,
            String destId,
            BigDecimal amount,
            String txnId) {

        String first =
                sourceId.compareTo(destId) <= 0
                        ? sourceId
                        : destId;

        String second =
                sourceId.compareTo(destId) <= 0
                        ? destId
                        : sourceId;

        TransferResult result =
                oracleTx.execute(status -> {

                    AccountMaster firstAcct =
                            accountRepository
                                    .findByIdForUpdate(first)
                                    .orElseThrow(() ->
                                            new ResourceNotFoundException(
                                                    "Account "
                                                            + first
                                                            + " not found"));

                    AccountMaster secondAcct =
                            accountRepository
                                    .findByIdForUpdate(second)
                                    .orElseThrow(() ->
                                            new ResourceNotFoundException(
                                                    "Account "
                                                            + second
                                                            + " not found"));

                    AccountMaster source =
                            first.equals(sourceId)
                                    ? firstAcct
                                    : secondAcct;

                    AccountMaster dest =
                            first.equals(sourceId)
                                    ? secondAcct
                                    : firstAcct;

                    assertActive(source);
                    assertActive(dest);

                    BigDecimal sourceBefore =
                            source.getBalanceAmount();

                    BigDecimal sourceAfter =
                            sourceBefore.subtract(amount);

                    if (sourceAfter.compareTo(
                            BigDecimal.ZERO) < 0) {

                        throw new InsufficientBalanceException(
                                "Account "
                                        + sourceId
                                        + " has insufficient balance for this transfer");
                    }

                    BigDecimal destBefore =
                            dest.getBalanceAmount();

                    BigDecimal destAfter =
                            destBefore.add(amount);

                    LocalDateTime now =
                            LocalDateTime.now();

                    source.setBalanceAmount(sourceAfter);
                    source.setUpdatedAt(now);
                    source.setUpdatedBy("SYSTEM");

                    dest.setBalanceAmount(destAfter);
                    dest.setUpdatedAt(now);
                    dest.setUpdatedBy("SYSTEM");

                    accountRepository.save(source);
                    accountRepository.save(dest);

                    TransactionMaster txnMaster =
                            TransactionMaster.builder()
                                    .txnId(txnId)
                                    .txnType("TRANSFER")
                                    .debitAccountId(sourceId)
                                    .creditAccountId(destId)
                                    .mutationAmount(amount)
                                    .txnStatus("PENDING")
                                    .initiatedAt(now)
                                    .createdAt(now)
                                    .createdBy("SYSTEM")
                                    .build();

                    txnMasterRepository.save(txnMaster);

                    return new TransferResult(
                            new MutationResult(
                                    sourceBefore,
                                    sourceAfter,
                                    amount.negate()),
                            new MutationResult(
                                    destBefore,
                                    destAfter,
                                    amount));
                });

        if (result == null) {
            throw new IllegalStateException(
                    "Oracle transfer transaction returned no result");
        }

        return result;
    }

    // ── Dual-write Phase 2 + 3 ────────────────────────────────────────────────

    private void persistAuditOrCompensate(
            String txnId,
            String accountId,
            String txnType,
            String mutationType,
            BigDecimal amount,
            MutationResult result) {

        try {

            postgresTx.executeWithoutResult(status ->
                    auditRepository.save(
                            LedgerMutationAudit.builder()
                                    .txnId(txnId)
                                    .accountId(accountId)
                                    .mutationAmount(amount)
                                    .mutationType(mutationType)
                                    .txnType(txnType)
                                    .auditState("COMMITTED")
                                    .createdAt(Instant.now())
                                    .build()));

            commitTxnStatus(txnId);

            balanceCacheInvalidator.evict(accountId);

        } catch (RuntimeException ex) {

            log.error(
                    "Ledger audit write failed for txn {}; "
                            + "compensating Oracle on account {}",
                    txnId,
                    accountId,
                    ex);

            compensate(
                    accountId,
                    result.appliedDelta(),
                    txnId);

            throw new LedgerPersistenceException(
                    "Failed to persist ledger audit for txn "
                            + txnId
                            + "; balance mutation was rolled back",
                    ex);
        }
    }

    private void persistTransferAuditOrCompensate(
            String txnId,
            TransactionRequest request,
            TransferResult result) {

        try {

            postgresTx.executeWithoutResult(status -> {

                auditRepository.save(
                        LedgerMutationAudit.builder()
                                .txnId(txnId)
                                .accountId(
                                        request.accountId())
                                .mutationAmount(
                                        request.amount())
                                .mutationType("DEBIT")
                                .txnType("TRANSFER")
                                .auditState("COMMITTED")
                                .createdAt(Instant.now())
                                .build());

                auditRepository.save(
                        LedgerMutationAudit.builder()
                                .txnId(txnId)
                                .accountId(
                                        request.counterpartyAccountId())
                                .mutationAmount(
                                        request.amount())
                                .mutationType("CREDIT")
                                .txnType("TRANSFER")
                                .auditState("COMMITTED")
                                .createdAt(Instant.now())
                                .build());
            });

            commitTxnStatus(txnId);

            balanceCacheInvalidator.evict(
                    request.accountId());

            balanceCacheInvalidator.evict(
                    request.counterpartyAccountId());

        } catch (RuntimeException ex) {

            log.error(
                    "Ledger audit write failed for transfer txn {}; "
                            + "compensating both legs",
                    txnId,
                    ex);

            compensate(
                    request.accountId(),
                    result.sourceResult().appliedDelta(),
                    txnId);

            compensate(
                    request.counterpartyAccountId(),
                    result.destResult().appliedDelta(),
                    txnId);

            throw new LedgerPersistenceException(
                    "Failed to persist ledger audit for transfer txn "
                            + txnId
                            + "; balances were rolled back",
                    ex);
        }
    }

    // ── Compensation & Status ─────────────────────────────────────────────────

    private void compensate(
            String accountId,
            BigDecimal appliedDelta,
            String txnId) {

        try {

            oracleTx.executeWithoutResult(status -> {

                AccountMaster account =
                        accountRepository
                                .findByIdForUpdate(accountId)
                                .orElseThrow(() ->
                                        new ResourceNotFoundException(
                                                "Account "
                                                        + accountId
                                                        + " not found during compensation"));

                LocalDateTime now =
                        LocalDateTime.now();

                account.setBalanceAmount(
                        account.getBalanceAmount()
                                .add(appliedDelta.negate()));

                account.setUpdatedAt(now);
                account.setUpdatedBy("SYSTEM");

                accountRepository.save(account);

                txnMasterRepository.findById(txnId)
                        .ifPresent(txn -> {

                            txn.setTxnStatus(
                                    "ROLLED_BACK");

                            txn.setCompletedAt(now);
                            txn.setUpdatedAt(now);
                            txn.setUpdatedBy("SYSTEM");

                            txnMasterRepository.save(txn);
                        });
            });

            balanceCacheInvalidator.evict(accountId);

        } catch (RuntimeException compensationEx) {

            log.error(
                    "CRITICAL: compensation failed for account {} "
                            + "(delta {}, txnId {}). Manual reconciliation required.",
                    accountId,
                    appliedDelta,
                    txnId,
                    compensationEx);

            throw compensationEx;
        }
    }

    private void commitTxnStatus(String txnId) {

        oracleTx.executeWithoutResult(status ->
                txnMasterRepository.findById(txnId)
                        .ifPresent(txn -> {

                            LocalDateTime now =
                                    LocalDateTime.now();

                            txn.setTxnStatus(
                                    "COMMITTED");

                            txn.setCompletedAt(now);
                            txn.setUpdatedAt(now);
                            txn.setUpdatedBy("SYSTEM");

                            txnMasterRepository.save(txn);
                        }));
    }

    private void assertActive(
            AccountMaster account) {

        if (!"ACTIVE".equals(
                account.getAccountStatus())) {

            throw new IllegalStateException(
                    "Account "
                            + account.getAccountId()
                            + " is not ACTIVE (status="
                            + account.getAccountStatus()
                            + ")");
        }
    }
}