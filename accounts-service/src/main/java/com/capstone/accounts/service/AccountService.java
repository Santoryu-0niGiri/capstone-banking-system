
package com.capstone.accounts.service;

import com.capstone.accounts.entity.CustomerBalanceMaster;
import com.capstone.accounts.repository.AccountRepository;
import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.CreateAccountRequest;
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
    private final BalanceCacheService balanceCacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Inserts a row into CUSTOMER_BALANCE_MASTER and seeds the balance cache.
     * Publishes account.created after the DB commit.
     */
    @Transactional
    public AccountDTO createAccount(CreateAccountRequest request) {
        String accountId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        CustomerBalanceMaster account = CustomerBalanceMaster.builder()
                .accountId(accountId)
                .customerId(request.customerId())
                .accountType(request.accountType())
                .currencyCode(request.currencyCode())
                .accountStatus("ACTIVE")
                .balanceAmount(BigDecimal.ZERO)
                .createdAt(now)
                .createdBy("SYSTEM")
                .version(0L)
                .build();

        CustomerBalanceMaster saved = accountRepository.save(account);
        balanceCacheService.put(saved.getAccountId(), saved.getBalanceAmount());

        AccountDTO dto = toDto(saved);
        kafkaTemplate.send(KafkaTopics.ACCOUNT_CREATED, saved.getAccountId(), dto)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish account.created for accountId={}", saved.getAccountId(), ex);
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

    /**
     * Cache-aside read: Redis hit returns immediately; miss falls through to
     * Oracle and refreshes the cache entry.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountId) {
        return balanceCacheService.get(accountId)
                .orElseGet(() -> {
                    CustomerBalanceMaster acct = findOrThrow(accountId);
                    balanceCacheService.put(accountId, acct.getBalanceAmount());
                    return acct.getBalanceAmount();
                });
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private CustomerBalanceMaster findOrThrow(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + accountId + " not found"));
    }

    private AccountDTO toDto(CustomerBalanceMaster a) {
        return new AccountDTO(
                a.getAccountId(),
                a.getCustomerId(),
                a.getAccountType(),
                a.getAccountStatus(),
                a.getBalanceAmount(),
                a.getCurrencyCode(),
                a.getCreatedAt()
        );
    }
}

