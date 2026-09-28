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
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.AccountMutationResponse;
import com.capstone.common.event.ForexConversionRequestedEvent;
import com.capstone.transaction.client.AccountsServiceClient;
import com.capstone.transaction.entity.oracle.TransactionMaster;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.entity.postgres.TransactionOutbox;
import com.capstone.transaction.kafka.TransactionEventProducer;
import com.capstone.transaction.model.MutationResult;
import com.capstone.transaction.model.TransferResult;
import com.capstone.transaction.repository.oracle.TransactionMasterRepository;
import com.capstone.transaction.repository.postgres.LedgerMutationAuditRepository;
import com.capstone.transaction.repository.postgres.TransactionOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.capstone.common.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core balance mutation orchestrator: WITHDRAWAL, DEPOSIT, TRANSFER.
 *
 * Balance mutation and row locking are delegated to Accounts Service via REST
 * (which executes SELECT … FOR UPDATE on ACCOUNT_MASTER with a 5-second timeout).
 * Transaction Service owns TRANSACTION_MASTER in Oracle and LEDGER_MUTATION_AUDIT in PostgreSQL.
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
    private final TransactionOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
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
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            TransactionOutboxRepository outboxRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ObjectMapper objectMapper) {

        this.accountsServiceClient = accountsServiceClient;
        this.txnMasterRepository = txnMasterRepository;
        this.auditRepository = auditRepository;
        this.oracleTx = new TransactionTemplate(oracleTxManager);
        this.postgresTx = new TransactionTemplate(postgresTxManager);
        this.idempotencyService = idempotencyService;
        this.eventProducer = eventProducer;
        this.balanceCacheInvalidator = balanceCacheInvalidator;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

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
            BalanceCacheInvalidator balanceCacheInvalidator) {

        this(accountsServiceClient, txnMasterRepository, auditRepository, oracleTxManager, postgresTxManager,
                idempotencyService, eventProducer, balanceCacheInvalidator, null, null);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public TransactionResponse withdraw(
            TransactionRequest request,
            UUID txnId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !SecurityUtils.isPrivileged()) {
            AccountDTO acct = accountsServiceClient.getAccount(request.accountId());
            if (acct != null) {
                SecurityUtils.checkCustomerAccess(acct.customerId(), "withdraw from account " + request.accountId());
            }
        }

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

        AccountDTO srcAcct = accountsServiceClient.getAccount(request.accountId());
        AccountDTO destAcct = accountsServiceClient.getAccount(request.counterpartyAccountId());

        if (srcAcct != null) {
            SecurityUtils.checkCustomerAccess(srcAcct.customerId(), "transfer from account " + request.accountId());
        }

        boolean isCrossCurrency = srcAcct != null && destAcct != null
                && srcAcct.currencyCode() != null && destAcct.currencyCode() != null
                && !srcAcct.currencyCode().equalsIgnoreCase(destAcct.currencyCode());

        if (isCrossCurrency) {
            return executeCrossCurrencyTransfer(request, txnId, srcAcct, destAcct);
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

        List<LedgerMutationAudit> audits = auditRepository.findByTxnId(txnId);
        if (!audits.isEmpty() && !SecurityUtils.isPrivileged()) {
            String currentCustId = SecurityUtils.getCurrentCustomerId().orElse(null);
            if (currentCustId != null) {
                boolean ownsLeg = audits.stream().anyMatch(a -> {
                    try {
                        AccountDTO acct = accountsServiceClient.getAccount(a.getAccountId());
                        return acct != null && currentCustId.equalsIgnoreCase(acct.customerId());
                    } catch (Exception ignored) {
                        return false;
                    }
                });
                if (!ownsLeg) {
                    throw new AccessDeniedException("Access denied: You do not have permission to view audit for transaction " + txnId);
                }
            }
        }

        return audits;
    }

    /**
     * Finds all PostgreSQL audit records for a given account.
     * Enforces customer ownership check for non-privileged users.
     */
    public List<LedgerMutationAudit> findAuditByAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID is required");
        }

        if (!SecurityUtils.isPrivileged()) {
            try {
                AccountDTO acct = accountsServiceClient.getAccount(accountId);
                if (acct != null) {
                    SecurityUtils.checkCustomerAccess(acct.customerId(), "view audit for account " + accountId);
                }
            } catch (Exception ignored) {
            }
        }

        return auditRepository.findByAccountId(accountId);
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

    private TransactionResponse executeCrossCurrencyTransfer(
            TransactionRequest request,
            UUID txnId,
            AccountDTO srcAcct,
            AccountDTO destAcct) {

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
            LocalDateTime now = LocalDateTime.now();
            ForexConversionRequestedEvent outboxEvent = new ForexConversionRequestedEvent(
                    txnId,
                    request.accountId(),
                    request.counterpartyAccountId(),
                    srcAcct.currencyCode(),
                    destAcct.currencyCode(),
                    request.amount(),
                    Instant.now()
            );

            // Phase 1: Oracle — Record TRANSACTION_MASTER (PENDING, cross-currency)
            oracleTx.executeWithoutResult(status -> {
                TransactionMaster txnMaster = TransactionMaster.builder()
                        .txnId(txnId.toString())
                        .txnType("TRANSFER")
                        .debitAccountId(request.accountId())
                        .creditAccountId(request.counterpartyAccountId())
                        .mutationAmount(request.amount())
                        .isCrossCurrency("Y")
                        .txnStatus("PENDING")
                        .initiatedAt(now)
                        .createdAt(now)
                        .createdBy("SYSTEM")
                        .build();

                txnMasterRepository.save(txnMaster);
            });

            // Phase 2: PostgreSQL — Queue FOREX_CONVERSION_REQUESTED to outbox_audit
            if (outboxRepository != null) {
                postgresTx.executeWithoutResult(status -> {
                    try {
                        TransactionOutbox outbox = TransactionOutbox.builder()
                                .sourceService("transaction-service")
                                .aggregateType("TRANSACTION")
                                .aggregateId(txnId.toString())
                                .eventType(KafkaTopics.FOREX_CONVERSION_REQUESTED)
                                .payload(objectMapper.writeValueAsString(outboxEvent))
                                .status("PENDING")
                                .createdAt(OffsetDateTime.now())
                                .build();

                        outboxRepository.save(outbox);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to serialize ForexConversionRequestedEvent to outbox_audit", e);
                    }
                });
            } else {
                eventProducer.publishToTopic(
                        KafkaTopics.FOREX_CONVERSION_REQUESTED,
                        txnId.toString(),
                        outboxEvent);
            }

            eventProducer.publishCreated(
                    new TransactionCreatedEvent(
                            txnId,
                            request.accountId(),
                            request.counterpartyAccountId(),
                            "TRANSFER",
                            request.amount(),
                            Instant.now()));

            TransactionResponse response = new TransactionResponse(
                    txnId,
                    request.accountId(),
                    "TRANSFER",
                    request.amount(),
                    srcAcct.balanceAmount(),
                    "PENDING",
                    Instant.now(),
                    destAcct.currencyCode(),
                    null,
                    null,
                    null,
                    true
            );

            idempotencyService.storeResult(
                    request.idempotencyKey(),
                    response);

            return response;

        } catch (RuntimeException ex) {
            idempotencyService.release(request.idempotencyKey());

            eventProducer.publishFailed(
                    new TransactionFailedEvent(
                            txnId,
                            request.accountId(),
                            request.counterpartyAccountId(),
                            "TRANSFER",
                            request.amount(),
                            ex.getMessage(),
                            Instant.now()));

            throw ex;
        }
    }

    // ── Phase 1: Accounts Service REST + Oracle TRANSACTION_MASTER ────────────

    private MutationResult applyDelta(
            String accountId,
            BigDecimal delta,
            String txnId,
            String txnType,
            String debitAccountId,
            String creditAccountId) {

        LocalDateTime now = LocalDateTime.now();

        // Phase 1a: Record TransactionMaster in Oracle as PENDING
        oracleTx.executeWithoutResult(status -> {
            TransactionMaster txnMaster =
                    TransactionMaster.builder()
                            .txnId(txnId)
                            .txnType(txnType)
                            .debitAccountId(debitAccountId)
                            .creditAccountId(creditAccountId)
                            .mutationAmount(delta.abs())
                            .isCrossCurrency("N")
                            .txnStatus("PENDING")
                            .initiatedAt(now)
                            .createdAt(now)
                            .createdBy("SYSTEM")
                            .build();

            txnMasterRepository.save(txnMaster);
        });

        // Phase 1b: Mutate balance in Accounts Service via REST under pessimistic lock
        AccountMutationResponse response;
        try {
            if (delta.compareTo(BigDecimal.ZERO) < 0) {
                response = accountsServiceClient.debit(accountId, delta.abs(), txnId, txnType);
            } else {
                response = accountsServiceClient.credit(accountId, delta.abs(), txnId, txnType);
            }
        } catch (RuntimeException ex) {
            rollbackTxnStatus(txnId);
            throw ex;
        }

        return new MutationResult(
                response.balanceBefore(),
                response.balanceAfter(),
                delta);
    }

    private TransferResult applyTransfer(
            String sourceId,
            String destId,
            BigDecimal amount,
            String txnId) {

        LocalDateTime now = LocalDateTime.now();

        // Phase 1a: Record TransactionMaster in Oracle as PENDING
        oracleTx.executeWithoutResult(status -> {
            TransactionMaster txnMaster =
                    TransactionMaster.builder()
                            .txnId(txnId)
                            .txnType("TRANSFER")
                            .debitAccountId(sourceId)
                            .creditAccountId(destId)
                            .mutationAmount(amount)
                            .isCrossCurrency("N")
                            .txnStatus("PENDING")
                            .initiatedAt(now)
                            .createdAt(now)
                            .createdBy("SYSTEM")
                            .build();

            txnMasterRepository.save(txnMaster);
        });

        // Phase 1b: Debit source account via Accounts Service REST
        AccountMutationResponse sourceResponse;
        try {
            sourceResponse = accountsServiceClient.debit(sourceId, amount, txnId, "TRANSFER");
        } catch (RuntimeException ex) {
            rollbackTxnStatus(txnId);
            throw ex;
        }

        // Phase 1c: Credit destination account via Accounts Service REST
        AccountMutationResponse destResponse;
        try {
            destResponse = accountsServiceClient.credit(destId, amount, txnId, "TRANSFER");
        } catch (RuntimeException ex) {
            // Source was debited, but dest credit failed -> Compensate source account!
            log.error("Transfer destination credit failed for txn {}. Compensating source account {}.",
                    txnId, sourceId, ex);
            try {
                accountsServiceClient.credit(sourceId, amount, txnId, "TRANSFER_COMPENSATION");
            } catch (Exception compEx) {
                log.error("CRITICAL: Failed to compensate source account {} for txn {}",
                        sourceId, txnId, compEx);
            }
            rollbackTxnStatus(txnId);
            throw ex;
        }

        return new TransferResult(
                new MutationResult(
                        sourceResponse.balanceBefore(),
                        sourceResponse.balanceAfter(),
                        amount.negate()),
                new MutationResult(
                        destResponse.balanceBefore(),
                        destResponse.balanceAfter(),
                        amount));
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
            // Reverse the applied delta via Accounts Service REST
            if (appliedDelta.compareTo(BigDecimal.ZERO) < 0) {
                // Was DEBIT -> reverse with CREDIT
                accountsServiceClient.credit(accountId, appliedDelta.abs(), txnId, "COMPENSATION");
            } else {
                // Was CREDIT -> reverse with DEBIT
                accountsServiceClient.debit(accountId, appliedDelta.abs(), txnId, "COMPENSATION");
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
        } catch (Exception ex) {
            log.error("Failed to mark txn {} as ROLLED_BACK: {}", txnId, ex.getMessage(), ex);
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