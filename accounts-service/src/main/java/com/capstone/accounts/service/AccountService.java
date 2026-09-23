package com.capstone.accounts.service;

import com.capstone.accounts.entity.CustomerBalanceMaster;
import com.capstone.accounts.repository.AccountRepository;
import com.capstone.accounts.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;
    private final BalanceCacheService balanceCacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Creates an account for an existing customer.
     *
     * CUSTOMER_MASTER must already contain the supplied customerId.
     * The database foreign key FK_BALANCE_MASTER_CUSTOMER remains unchanged.
     */
    @Transactional
    public AccountDTO createAccount(CreateAccountRequest request) {

        /*
         * Validate the parent customer before inserting into
         * CUSTOMER_BALANCE_MASTER.
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

        CustomerBalanceMaster account = CustomerBalanceMaster.builder()
                .accountId(accountId)
                .customerId(request.customerId())
                .accountType(request.accountType())
                .currencyCode(request.currencyCode())
                .accountStatus("ACTIVE")
                .balanceAmount(BigDecimal.ZERO)
                .createdAt(now)
                .createdBy("SYSTEM")
                .build();

        CustomerBalanceMaster saved = accountRepository.save(account);

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

    /**
     * Cache-aside read:
     * Redis hit returns immediately.
     * Cache miss falls through to Oracle and refreshes Redis.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountId) {
        return balanceCacheService.get(accountId)
                .orElseGet(() -> {
                    CustomerBalanceMaster acct = findOrThrow(accountId);

                    balanceCacheService.put(
                            accountId,
                            acct.getBalanceAmount()
                    );

                    return acct.getBalanceAmount();
                });
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private CustomerBalanceMaster findOrThrow(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account " + accountId + " not found"
                        )
                );
    }

    private AccountDTO toDto(CustomerBalanceMaster account) {
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