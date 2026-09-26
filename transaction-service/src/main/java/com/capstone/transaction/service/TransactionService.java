package com.capstone.transaction.service;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.TransactionRequest;
import com.capstone.common.dto.TransactionResponse;
import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.common.exception.IdempotencyConflictException;
import com.capstone.common.dto.AccountMutationResponse;
import com.capstone.common.exception.InsufficientBalanceException;
import com.capstone.common.exception.LedgerPersistenceException;
import com.capstone.common.exception.ResourceNotFoundException;
import com.capstone.transaction.client.AccountsServiceClient;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.model.MutationResult;
import com.capstone.transaction.model.TransferResult;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * ── Architecture ─────────────────────────────────────────────────────────────
 * Delegates balance mutations to AccountsServiceClient (FC-38/39/40), which
 * enforces PESSIMISTIC_WRITE locking at the Oracle row level in Accounts Service.
 *
 * ── Three-phase write per transaction ────────────────────────────────────────
 * Phase 1: Accounts Service applies balance mutation under row lock;
 *          Transaction Service records TRANSACTION_MASTER (txn_status=PENDING).
 * Phase 2 (PostgreSQL): Append double-entry rows to ledger_mutation_audit.
 * Phase 3 (Oracle): Update TRANSACTION_MASTER txn_status → COMMITTED.
 *
 * If Phase 2 fails → compensating mutation reverses the balance in Accounts
 * Service AND sets txn_status=ROLLED_BACK, then throws LedgerPersistenceException.
 */
@Service
@Slf4j
public class TransactionService {

    private final AccountsServiceClient accountsServiceClient;
    private final TransactionMasterRepository txnMasterRepository;
    private final LedgerMutationAuditRepository auditRepository;
    private final TransactionTemplate oracleTx;
    private final TransactionTemplate postgresTx;
    private final IdempotencyService idempotencyService;
    private final TransactionEventProducer eventProducer;
    private final BalanceCacheInvalidator balanceCacheInvalidator;
    private final com.capstone.transaction.repository.postgres.TransactionOutboxRepository outboxRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final FxRateService fxRateService;
    private final com.capstone.transaction.repository.postgres.FxConversionAuditRepository fxConversionAuditRepository;

    @Autowired
    public TransactionService(
            AccountsServiceClient accountsServiceClient,
            TransactionMasterRepository txnMasterRepository,
            LedgerMutationAuditRepository auditRepository,
            @Qualifier("oracleTransactionManager")
            PlatformTransactionManager oracleTxManager,
            @Qualifier("postgresTransactionManager")
            PlatformTransactionManager postgresTxManager,
            IdempotencyService idempotencyService,
            TransactionEventProducer eventProducer,
            BalanceCacheInvalidator balanceCacheInvalidator,
            @Autowired(required = false)
            com.capstone.transaction.repository.postgres.TransactionOutboxRepository outboxRepository,
            @Autowired(required = false)
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @Autowired(required = false)
            FxRateService fxRateService,
            @Autowired(required = false)
            com.capstone.transaction.repository.postgres.FxConversionAuditRepository fxConversionAuditRepository) {

        this.accountsServiceClient = accountsServiceClient;
        this.txnMasterRepository = txnMasterRepository;
        this.auditRepository = auditRepository;
        this.oracleTx = new TransactionTemplate(oracleTxManager);
        this.postgresTx = new TransactionTemplate(postgresTxManager);
        this.idempotencyService = idempotencyService;
        this.eventProducer = eventProducer;
        this.balanceCacheInvalidator = balanceCacheInvalidator;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.fxRateService = fxRateService;
        this.fxConversionAuditRepository = fxConversionAuditRepository;
    }

    public TransactionService(
            AccountsServiceClient accountsServiceClient,
            TransactionMasterRepository txnMasterRepository,
            LedgerMutationAuditRepository auditRepository,
            PlatformTransactionManager oracleTxManager,
            PlatformTransactionManager postgresTxManager,
            IdempotencyService idempotencyService,
            TransactionEventProducer eventProducer,
            BalanceCacheInvalidator balanceCacheInvalidator) {

        this(accountsServiceClient, txnMasterRepository, auditRepository,
                oracleTxManager, postgresTxManager, idempotencyService,
                eventProducer, balanceCacheInvalidator, null, null, null, null);
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

            rollbackTxnStatus(txnId);

            eventProducer.publishFailed(
                    new TransactionFailedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            null,
                            txnType,
                            request.amount(),
                            ex.getMessage(),
                            Instant.now()));

            saveOutbox(txnId, "TRANSACTION_FAILED",
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

            // Phase 1: Accounts Service debits source + credits destination
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
                            Instant.now(),
                            transferResult.targetCurrency(),
                            transferResult.fxRate(),
                            transferResult.destAmount(),
                            null, // feeAmount
                            transferResult.isCrossCurrency()));

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
                            Instant.now(),
                            transferResult.targetCurrency(),
                            transferResult.fxRate(),
                            transferResult.destAmount(),
                            null, // feeAmount
                            transferResult.isCrossCurrency());

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
                            Instant.now(),
                            transferResult.targetCurrency(),
                            transferResult.fxRate(),
                            transferResult.destAmount(),
                            null, // feeAmount
                            transferResult.isCrossCurrency()));

            return response;

        } catch (RuntimeException ex) {

            idempotencyService.release(
                    request.idempotencyKey());

            rollbackTxnStatus(txnId);

            eventProducer.publishFailed(
                    new TransactionFailedEvent(
                            UUID.fromString(txnId),
                            request.accountId(),
                            request.counterpartyAccountId(),
                            "TRANSFER",
                            request.amount(),
                            ex.getMessage(),
                            Instant.now()));

            saveOutbox(txnId, "TRANSACTION_FAILED",
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

    // ── Helper ──────────────────────────────────────────────────────────────

    private void saveOutbox(String aggregateId, String eventType, Object event) {
        if (outboxRepository == null || objectMapper == null) {
            return;
        }
        try {
            outboxRepository.save(
                    com.capstone.transaction.entity.postgres.TransactionOutbox.builder()
                            .aggregateType("TRANSACTION")
                            .aggregateId(aggregateId)
                            .eventType(eventType)
                            .payload(objectMapper.writeValueAsString(event))
                            .status("PENDING")
                            .createdAt(java.time.OffsetDateTime.now())
                            .build());
        } catch (Exception e) {
            log.error("Failed to save to outbox: {}", e.getMessage());
            throw new LedgerPersistenceException("Outbox write failed", e);
        }
    }

    // ── Phase 1 Mutations ───────────────────────────────────────────────────

    private MutationResult applyDelta(
            String accountId,
            BigDecimal delta,
            String txnId,
            String txnType,
            String debitAccountId,
            String creditAccountId) {

        AccountMutationResponse mutationResponse;
        if ("DEBIT".equals(txnType) || "WITHDRAWAL".equals(txnType) || delta.compareTo(BigDecimal.ZERO) < 0) {
            mutationResponse = accountsServiceClient.debit(accountId, delta.abs(), txnId, txnType);
        } else {
            mutationResponse = accountsServiceClient.credit(accountId, delta.abs(), txnId, txnType);
        }

        LocalDateTime now = LocalDateTime.now();
        oracleTx.executeWithoutResult(status -> {
            TransactionMaster txnMaster = TransactionMaster.builder()
                    .txnId(txnId)
                    .txnType(txnType)
                    .debitAccountId(debitAccountId)
                    .creditAccountId(creditAccountId)
                    .mutationAmount(delta.abs())
                    .txnStatus("PENDING")
                    .initiatedAt(now)
                    .createdAt(now)
                    .createdBy("SYSTEM")
                    .build();

            txnMasterRepository.save(txnMaster);
        });

        return new MutationResult(
                mutationResponse.balanceBefore(),
                mutationResponse.balanceAfter(),
                delta);
    }

    private TransferResult applyTransfer(
            String sourceId,
            String destId,
            BigDecimal amount,
            String txnId) {

        AccountMutationResponse sourceResp =
                accountsServiceClient.debit(sourceId, amount, txnId, "TRANSFER");

        AccountMutationResponse destResp;
        try {
            destResp = accountsServiceClient.credit(destId, amount, txnId, "TRANSFER");
        } catch (RuntimeException creditEx) {
            log.error("Transfer failed crediting destination {}; compensating source {}", destId, sourceId);
            try {
                accountsServiceClient.credit(sourceId, amount, txnId, "TRANSFER_COMPENSATION");
            } catch (Exception compEx) {
                log.error("CRITICAL: Failed to compensate source account {} after destination credit failure", sourceId, compEx);
            }
            throw creditEx;
        }

        boolean isCrossCurrency = sourceResp.currencyCode() != null
                && destResp.currencyCode() != null
                && !sourceResp.currencyCode().equalsIgnoreCase(destResp.currencyCode());

        BigDecimal fxRate = null;
        BigDecimal destAmount = amount;
        if (isCrossCurrency && fxRateService != null) {
            fxRate = fxRateService.getExchangeRate(sourceResp.currencyCode(), destResp.currencyCode());
            if (fxRate != null) {
                destAmount = amount.multiply(fxRate).setScale(4, java.math.RoundingMode.HALF_UP);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        final BigDecimal finalFxRate = fxRate;
        final BigDecimal finalDestAmount = destAmount;
        final boolean finalIsCrossCurrency = isCrossCurrency;

        oracleTx.executeWithoutResult(status -> {
            TransactionMaster txnMaster = TransactionMaster.builder()
                    .txnId(txnId)
                    .txnType("TRANSFER")
                    .debitAccountId(sourceId)
                    .creditAccountId(destId)
                    .mutationAmount(amount)
                    .isCrossCurrency(finalIsCrossCurrency ? "Y" : "N")
                    .fxRate(finalFxRate)
                    .destAmount(finalIsCrossCurrency ? finalDestAmount : null)
                    .txnStatus("PENDING")
                    .initiatedAt(now)
                    .createdAt(now)
                    .createdBy("SYSTEM")
                    .build();

            txnMasterRepository.save(txnMaster);
        });

        return new TransferResult(
                new MutationResult(sourceResp.balanceBefore(), sourceResp.balanceAfter(), sourceResp.appliedDelta()),
                new MutationResult(destResp.balanceBefore(), destResp.balanceAfter(), destResp.appliedDelta()),
                isCrossCurrency,
                fxRate,
                isCrossCurrency ? destAmount : null,
                sourceResp.currencyCode(),
                destResp.currencyCode());
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

            postgresTx.executeWithoutResult(status -> {

                auditRepository.save(
                        LedgerMutationAudit.builder()
                                .txnId(txnId)
                                .accountId(accountId)
                                .mutationAmount(amount)
                                .mutationType(mutationType)
                                .txnType(txnType)
                                .auditState("COMMITTED")
                                .createdAt(Instant.now())
                                .build());

                // Save outbox events within the same transaction
                saveOutbox(txnId, "TRANSACTION_CREATED",
                        new com.capstone.common.event.TransactionCreatedEvent(
                                UUID.fromString(txnId),
                                accountId,
                                null,
                                txnType,
                                amount,
                                Instant.now()));

                saveOutbox(txnId, "TRANSACTION_COMPLETED",
                        new com.capstone.common.event.TransactionCompletedEvent(
                                UUID.fromString(txnId),
                                accountId,
                                null,
                                txnType,
                                amount,
                                result.balanceAfter(),
                                Instant.now()));
            });

            commitTxnStatus(txnId);

            balanceCacheInvalidator.evict(accountId);

        } catch (RuntimeException ex) {

            log.error(
                    "Ledger audit write failed for txn {}; "
                            + "compensating on account {}",
                    txnId,
                    accountId,
                    ex);

            compensate(
                    accountId,
                    result.appliedDelta(),
                    txnId,
                    "COMPENSATION");

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

                // credit audit record for second leg (can be different amount for cross-currency)
                auditRepository.save(
                        LedgerMutationAudit.builder()
                                .txnId(txnId)
                                .accountId(
                                        request.counterpartyAccountId())
                                .mutationAmount(
                                        result.destResult().appliedDelta().abs()) // positive amount for audit
                                .mutationType("CREDIT")
                                .txnType("TRANSFER")
                                .auditState("COMMITTED")
                                .createdAt(Instant.now())
                                .build());

                if (Boolean.TRUE.equals(result.isCrossCurrency()) && fxConversionAuditRepository != null) {
                    fxConversionAuditRepository.save(
                            com.capstone.transaction.entity.postgres.FxConversionAudit.builder()
                                    .txnId(txnId)
                                    .sourceCurrency(result.sourceCurrency())
                                    .destCurrency(result.targetCurrency())
                                    .sourceAmount(request.amount())
                                    .fxRate(result.fxRate())
                                    .destAmount(result.destAmount())
                                    .conversionStatus("COMPLETED")
                                    .createdAt(java.time.OffsetDateTime.now())
                                    .build());
                }

                saveOutbox(txnId, "TRANSACTION_CREATED",
                        new com.capstone.common.event.TransactionCreatedEvent(
                                java.util.UUID.fromString(txnId),
                                request.accountId(),
                                request.counterpartyAccountId(),
                                "TRANSFER",
                                request.amount(),
                                java.time.Instant.now()));

                saveOutbox(txnId, "TRANSACTION_COMPLETED",
                        new com.capstone.common.event.TransactionCompletedEvent(
                                java.util.UUID.fromString(txnId),
                                request.accountId(),
                                request.counterpartyAccountId(),
                                "TRANSFER",
                                request.amount(),
                                result.sourceResult().balanceAfter(),
                                java.time.Instant.now()));
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
                    txnId,
                    "COMPENSATION");

            compensate(
                    request.counterpartyAccountId(),
                    result.destResult().appliedDelta(),
                    txnId,
                    "COMPENSATION");

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
            String txnId,
            String reason) {

        try {
            if (appliedDelta.compareTo(BigDecimal.ZERO) < 0) {
                accountsServiceClient.credit(accountId, appliedDelta.abs(), txnId, reason != null ? reason : "COMPENSATION");
            } else if (appliedDelta.compareTo(BigDecimal.ZERO) > 0) {
                accountsServiceClient.debit(accountId, appliedDelta.abs(), txnId, reason != null ? reason : "COMPENSATION");
            }

            rollbackTxnStatus(txnId);
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

    private void rollbackTxnStatus(String txnId) {
        try {
            oracleTx.executeWithoutResult(status ->
                    txnMasterRepository.findById(txnId)
                            .ifPresent(txn -> {
                                LocalDateTime now = LocalDateTime.now();
                                txn.setTxnStatus("ROLLED_BACK");
                                txn.setCompletedAt(now);
                                txn.setUpdatedAt(now);
                                txn.setUpdatedBy("SYSTEM");
                                txnMasterRepository.save(txn);
                            }));
        } catch (Exception e) {
            log.warn("Failed to update txn {} status to ROLLED_BACK: {}", txnId, e.getMessage());
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
}