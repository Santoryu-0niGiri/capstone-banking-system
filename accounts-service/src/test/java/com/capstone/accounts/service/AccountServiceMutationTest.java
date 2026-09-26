package com.capstone.accounts.service;

import com.capstone.accounts.entity.AccountMaster;
import com.capstone.accounts.repository.AccountRepository;
import com.capstone.accounts.repository.CustomerRepository;
import com.capstone.common.dto.AccountMutationResponse;
import com.capstone.common.exception.InsufficientBalanceException;
import com.capstone.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceMutationTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private BalanceCacheService balanceCacheService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                customerRepository,
                balanceCacheService,
                kafkaTemplate
        );
    }

    @Test
    @DisplayName("debit: succeeds when balance is sufficient")
    void debit_sufficientBalance_success() {
        String accountId = "acct-123";
        AccountMaster account = AccountMaster.builder()
                .accountId(accountId)
                .customerId("cust-1")
                .accountStatus("ACTIVE")
                .currencyCode("PHP")
                .balanceAmount(new BigDecimal("1000.0000"))
                .createdAt(LocalDateTime.now())
                .build();

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(AccountMaster.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountMutationResponse response = accountService.debit(
                accountId,
                new BigDecimal("400.0000"),
                "txn-999",
                "WITHDRAWAL"
        );

        assertThat(response.balanceBefore()).isEqualByComparingTo("1000.0000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("600.0000");
        assertThat(response.appliedDelta()).isEqualByComparingTo("-400.0000");
        assertThat(response.currencyCode()).isEqualTo("PHP");

        ArgumentCaptor<AccountMaster> captor = ArgumentCaptor.forClass(AccountMaster.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceAmount()).isEqualByComparingTo("600.0000");

        verify(balanceCacheService).put(eq(accountId), eq(new BigDecimal("600.0000")));
    }

    @Test
    @DisplayName("debit: throws InsufficientBalanceException when balance is less than debit amount")
    void debit_insufficientBalance_throwsException() {
        String accountId = "acct-123";
        AccountMaster account = AccountMaster.builder()
                .accountId(accountId)
                .customerId("cust-1")
                .accountStatus("ACTIVE")
                .currencyCode("PHP")
                .balanceAmount(new BigDecimal("100.0000"))
                .createdAt(LocalDateTime.now())
                .build();

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.debit(
                accountId,
                new BigDecimal("500.0000"),
                "txn-999",
                "WITHDRAWAL"
        )).isInstanceOf(InsufficientBalanceException.class)
          .hasMessageContaining("insufficient balance");

        verify(accountRepository, never()).save(any());
        verify(balanceCacheService, never()).put(any(), any());
    }

    @Test
    @DisplayName("debit: throws ResourceNotFoundException when account does not exist")
    void debit_accountNotFound_throwsException() {
        String accountId = "non-existent";
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.debit(
                accountId,
                new BigDecimal("100.0000"),
                "txn-999",
                "WITHDRAWAL"
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("credit: increases balance and updates Redis cache")
    void credit_success() {
        String accountId = "acct-456";
        AccountMaster account = AccountMaster.builder()
                .accountId(accountId)
                .customerId("cust-2")
                .accountStatus("ACTIVE")
                .currencyCode("USD")
                .balanceAmount(new BigDecimal("250.0000"))
                .createdAt(LocalDateTime.now())
                .build();

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(AccountMaster.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountMutationResponse response = accountService.credit(
                accountId,
                new BigDecimal("750.0000"),
                "txn-888",
                "DEPOSIT"
        );

        assertThat(response.balanceBefore()).isEqualByComparingTo("250.0000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("1000.0000");
        assertThat(response.appliedDelta()).isEqualByComparingTo("750.0000");
        assertThat(response.currencyCode()).isEqualTo("USD");

        verify(accountRepository).save(any());
        verify(balanceCacheService).put(eq(accountId), eq(new BigDecimal("1000.0000")));
    }
}

