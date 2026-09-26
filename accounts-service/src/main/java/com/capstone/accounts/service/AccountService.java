package com.capstone.accounts.service;

import com.capstone.accounts.entity.AccountMaster;
import com.capstone.accounts.repository.AccountRepository;
import com.capstone.accounts.repository.CustomerRepository;
import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.AccountMutationResponse;
import com.capstone.common.dto.CreateAccountRequest;
import com.capstone.common.exception.InsufficientBalanceException;
import com.capstone.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final BalanceCacheService balanceCacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Creates an account for an existing customer.
     *
     * CUSTOMER_MASTER must already contain the supplied customerId.
     * The database foreign key FK_ACCOUNT_CUSTOMER remains unchanged.
     */
    @Transactional
    public AccountDTO createAccount(CreateAccountRequest request) {

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

        kafkaTemplate.send(
                KafkaTopics.ACCOUNT_CREATED,
                saved.getAccountId(),
                dto
        ).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn(
                        "Failed to publish account.created for accountId={}",
                        saved.getAccountId(),
                        ex
                );
            }
        });

        return dto;
    }

    @Transactional(readOnly = true)
    public AccountDTO getAccount(String accountId) {
        return toDto(findOrThrow(accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> getAccountsForCustomer(String customerId) {
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
        return balanceCacheService.get(accountId)
                .orElseGet(() -> {
                    AccountMaster acct = findOrThrow(accountId);

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

        return new AccountMutationResponse(
                accountId,
                before,
                after,
                amount,
                account.getCurrencyCode()
        );
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

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