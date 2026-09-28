package com.capstone.accounts.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.accounts.entity.AccountMaster;
import com.capstone.accounts.entity.OutboxMaster;
import com.capstone.accounts.repository.AccountRepository;
import com.capstone.accounts.repository.CustomerRepository;
import com.capstone.accounts.repository.OutboxMasterRepository;
import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.AccountMutationResponse;
import com.capstone.common.dto.CreateAccountRequest;
import com.capstone.common.event.BalanceUpdatedEvent;
import com.capstone.common.event.CrossCurrencySettlementCompletedEvent;
import com.capstone.common.event.ForexConversionCompletedEvent;
import com.capstone.common.exception.InsufficientBalanceException;
import com.capstone.common.exception.ResourceNotFoundException;
import com.capstone.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final BalanceCacheService balanceCacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxMasterRepository outboxMasterRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            BalanceCacheService balanceCacheService,
            KafkaTemplate<String, Object> kafkaTemplate,
            OutboxMasterRepository outboxMasterRepository,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.balanceCacheService = balanceCacheService;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxMasterRepository = Objects.requireNonNull(outboxMasterRepository, "outboxMasterRepository must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Creates an account for an existing customer.
     *
     * CUSTOMER_MASTER must already contain the supplied customerId.
     * The database foreign key FK_ACCOUNT_CUSTOMER remains unchanged.
     */
    @Transactional
    public AccountDTO createAccount(CreateAccountRequest request) {

        SecurityUtils.checkCustomerAccess(request.customerId(), "create account");

        /*
         * Validate the parent customer before inserting into
         * ACCOUNT_MASTER.
         *
         * This prevents Oracle ORA-02291 from being exposed to the client.
         */
        if (!customerRepository.existsById(request.customerId())) {
            throw new ResourceNotFoundException(
                    "Customer " + request.customerId() + " not found"
            );
        }

        String accountId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        AccountMaster account = AccountMaster.builder()
                .accountId(accountId)
                .customerId(request.customerId())
                .accountType(request.accountType())
                .currencyCode(request.currencyCode())
                .accountStatus("ACTIVE")
                .balanceAmount(BigDecimal.ZERO)
                .createdAt(now)
                .createdBy("SYSTEM")
                .build();

        AccountMaster saved = accountRepository.save(account);

        balanceCacheService.put(
                saved.getAccountId(),
                saved.getBalanceAmount()
        );

        AccountDTO dto = toDto(saved);

        // Save to transactional outbox in Oracle (atomic with account creation)
        saveOutbox("ACCOUNT", saved.getAccountId(), KafkaTopics.ACCOUNT_CREATED, dto);

        // Immediate direct publish attempt for fast notification
        if (kafkaTemplate != null) {
            kafkaTemplate.send(
                    KafkaTopics.ACCOUNT_CREATED,
                    saved.getAccountId(),
                    dto
            ).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Direct publish account.created failed for accountId={}; outbox relay will guarantee delivery",
                            saved.getAccountId());
                }
            });
        }

        return dto;
    }

    @Transactional(readOnly = true)
    public AccountDTO getAccount(String accountId) {
        AccountMaster account = findOrThrow(accountId);
        SecurityUtils.checkCustomerAccess(account.getCustomerId(), "view account " + accountId);
        return toDto(account);
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> getAccountsForCustomer(String customerId) {
        SecurityUtils.checkCustomerAccess(customerId, "view accounts");
        return accountRepository.findByCustomerId(customerId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Admin governance: freeze, unfreeze, or close an account.
     *
     * Allowed statuses: ACTIVE | FROZEN | CLOSED
     * Any in-flight mutation on a non-ACTIVE account will be rejected
     * by TransactionService (assertActive guard) before touching balances.
     */
    @Transactional
    public AccountDTO updateAccountStatus(String accountId, String newStatus) {
        if (!java.util.Set.of("ACTIVE", "FROZEN", "CLOSED").contains(newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid account status '" + newStatus
                            + "'. Allowed: ACTIVE, FROZEN, CLOSED");
        }

        AccountMaster account = findOrThrow(accountId);
        LocalDateTime now = LocalDateTime.now();

        account.setAccountStatus(newStatus);
        account.setUpdatedAt(now);
        account.setUpdatedBy("ADMIN");

        AccountMaster saved = accountRepository.save(account);

        // Evict cached balance so stale data is not served after a status change.
        balanceCacheService.evict(accountId);

        log.info("Admin updated account {} status to {}", accountId, newStatus);
        return toDto(saved);
    }

    /**
     * Cache-aside read:
     * Redis hit returns immediately.
     * Cache miss falls through to Oracle and refreshes Redis.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountId) {
        AccountMaster acct = findOrThrow(accountId);
        SecurityUtils.checkCustomerAccess(acct.getCustomerId(), "view balance of account " + accountId);
        return balanceCacheService.get(accountId)
                .orElseGet(() -> {
                    balanceCacheService.put(
                            accountId,
                            acct.getBalanceAmount()
                    );
                    return acct.getBalanceAmount();
                });
    }

    /**
     * Atomically debits the account under a PESSIMISTIC_WRITE row lock (SELECT ... FOR UPDATE).
     * Serializes concurrent debits and ensures balance never drops below zero.
     * Updates Redis balance cache immediately.
     */
    @Transactional
    public AccountMutationResponse debit(String accountId, BigDecimal amount, String txnId, String txnType) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be greater than zero");
        }

        AccountMaster account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + accountId + " not found"));

        SecurityUtils.checkCustomerAccess(account.getCustomerId(), "debit account " + accountId);

        assertActive(account);

        BigDecimal before = account.getBalanceAmount();
        BigDecimal after = before.subtract(amount);

        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Account " + accountId + " has insufficient balance");
        }

        LocalDateTime now = LocalDateTime.now();
        account.setBalanceAmount(after);
        account.setUpdatedAt(now);
        account.setUpdatedBy("SYSTEM");

        accountRepository.save(account);

        balanceCacheService.put(accountId, after);

        // Atomic outbox write
        saveOutbox("ACCOUNT", accountId, KafkaTopics.BALANCE_UPDATED,
                new BalanceUpdatedEvent(
                        accountId,
                        before,
                        after,
                        amount.negate(),
                        account.getCurrencyCode(),
                        txnId,
                        txnType,
                        Instant.now()
                ));

        return new AccountMutationResponse(
                accountId,
                before,
                after,
                amount.negate(),
                account.getCurrencyCode()
        );
    }

    /**
     * Atomically credits the account under a PESSIMISTIC_WRITE row lock (SELECT ... FOR UPDATE).
     * Updates Redis balance cache immediately.
     */
    @Transactional
    public AccountMutationResponse credit(String accountId, BigDecimal amount, String txnId, String txnType) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be greater than zero");
        }

        AccountMaster account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + accountId + " not found"));

        assertActive(account);

        BigDecimal before = account.getBalanceAmount();
        BigDecimal after = before.add(amount);

        LocalDateTime now = LocalDateTime.now();
        account.setBalanceAmount(after);
        account.setUpdatedAt(now);
        account.setUpdatedBy("SYSTEM");

        accountRepository.save(account);

        balanceCacheService.put(accountId, after);

        // Atomic outbox write
        saveOutbox("ACCOUNT", accountId, KafkaTopics.BALANCE_UPDATED,
                new BalanceUpdatedEvent(
                        accountId,
                        before,
                        after,
                        amount,
                        account.getCurrencyCode(),
                        txnId,
                        txnType,
                        Instant.now()
                ));

        return new AccountMutationResponse(
                accountId,
                before,
                after,
                amount,
                account.getCurrencyCode()
        );
    }

    /**
     * Settles a cross-currency transfer leg under pessimistic locks (FC-47).
     * Debits the source account and credits destination account with converted amount,
     * then queues a CrossCurrencySettlementCompletedEvent into OUTBOX_MASTER.
     */
    @Transactional
    public CrossCurrencySettlementCompletedEvent settleCrossCurrency(ForexConversionCompletedEvent event) {
        log.info("Settling cross-currency transfer txnId={} src={} dest={} srcAmt={} destAmt={}",
                event.txnId(), event.sourceAccountId(), event.destAccountId(), event.sourceAmount(), event.destAmount());

        AccountMaster source = accountRepository.findByIdForUpdate(event.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account " + event.sourceAccountId() + " not found"));

        Optional<OutboxMaster> existingSettlement = outboxMasterRepository
                .findFirstBySourceServiceAndAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        "accounts-service", event.txnId().toString(), KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED);
        if (existingSettlement.isPresent()) {
            log.info("Ignoring duplicate Forex settlement delivery txnId={}", event.txnId());
            try {
                return objectMapper.readValue(
                        existingSettlement.get().getPayload(), CrossCurrencySettlementCompletedEvent.class);
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Unable to read existing settlement result for txnId=" + event.txnId(), ex);
            }
        }

        assertActive(source);

        if (source.getBalanceAmount().compareTo(event.sourceAmount()) < 0) {
            log.error("Insufficient balance for cross-currency txnId={} on account={}", event.txnId(), source.getAccountId());
            throw new InsufficientBalanceException("Account " + source.getAccountId() + " has insufficient balance");
        }

        AccountMaster dest = accountRepository.findByIdForUpdate(event.destAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account " + event.destAccountId() + " not found"));
        assertActive(dest);

        BigDecimal srcBefore = source.getBalanceAmount();
        BigDecimal srcAfter = srcBefore.subtract(event.sourceAmount());
        source.setBalanceAmount(srcAfter);
        source.setUpdatedAt(LocalDateTime.now());
        source.setUpdatedBy("SYSTEM");
        accountRepository.save(source);
        balanceCacheService.put(source.getAccountId(), srcAfter);

        BigDecimal destBefore = dest.getBalanceAmount();
        BigDecimal destAfter = destBefore.add(event.destAmount());
        dest.setBalanceAmount(destAfter);
        dest.setUpdatedAt(LocalDateTime.now());
        dest.setUpdatedBy("SYSTEM");
        accountRepository.save(dest);
        balanceCacheService.put(dest.getAccountId(), destAfter);

        CrossCurrencySettlementCompletedEvent settlementEvent = new CrossCurrencySettlementCompletedEvent(
                event.txnId(),
                source.getAccountId(),
                dest.getAccountId(),
                event.sourceCurrency(),
                event.destCurrency(),
                event.sourceAmount(),
                event.fxRate(),
                event.destAmount(),
                srcAfter,
                destAfter,
                Instant.now()
        );

        saveOutbox("CROSS_CURRENCY", event.txnId().toString(), KafkaTopics.CROSSCURRENCY_SETTLEMENT_COMPLETED, settlementEvent);

        log.info("Successfully settled cross-currency transfer txnId={} srcAfter={} destAfter={}",
                event.txnId(), srcAfter, destAfter);

        return settlementEvent;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void saveOutbox(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String json = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
            OutboxMaster outbox = OutboxMaster.builder()
                    .outboxId(UUID.randomUUID().toString())
                    .sourceService("accounts-service")
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();
            outboxMasterRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to save to OutboxMaster: aggregateId={}, eventType={}", aggregateId, eventType, e);
            throw new RuntimeException("Failed to persist outbox event for aggregate " + aggregateId, e);
        }
    }

    private void assertActive(AccountMaster account) {
        if (!"ACTIVE".equalsIgnoreCase(account.getAccountStatus())) {
            throw new IllegalStateException("Account " + account.getAccountId() + " is " + account.getAccountStatus());
        }
    }

    private AccountMaster findOrThrow(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account " + accountId + " not found"
                        )
                );
    }

    private AccountDTO toDto(AccountMaster account) {
        return new AccountDTO(
                account.getAccountId(),
                account.getCustomerId(),
                account.getAccountType(),
                account.getAccountStatus(),
                account.getBalanceAmount(),
                account.getCurrencyCode(),
                account.getCreatedAt()
        );
    }
}