package com.capstone.accounts.service;

import com.capstone.accounts.entity.AccountMaster;
import com.capstone.accounts.repository.AccountRepository;
import com.capstone.accounts.repository.CustomerRepository;
import com.capstone.accounts.repository.OutboxMasterRepository;
import com.capstone.common.dto.CreateAccountRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceSecurityAndOutboxTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private BalanceCacheService balanceCacheService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private OutboxMasterRepository outboxMasterRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                customerRepository,
                balanceCacheService,
                kafkaTemplate,
                outboxMasterRepository,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String customerId, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                .toList();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(customerId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Security: Customer A debiting Customer B account throws 403 AccessDeniedException")
    void customerA_debitCustomerB_throwsAccessDenied() {
        authenticate("cust-A", "ROLE_CUSTOMER");

        AccountMaster accountB = AccountMaster.builder()
                .accountId("acct-B")
                .customerId("cust-B")
                .accountStatus("ACTIVE")
                .balanceAmount(new BigDecimal("1000.0000"))
                .currencyCode("PHP")
                .build();

        when(accountRepository.findByIdForUpdate("acct-B")).thenReturn(Optional.of(accountB));

        assertThatThrownBy(() ->
                accountService.debit("acct-B", new BigDecimal("100.0000"), "txn-1", "WITHDRAWAL")
        ).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cust-B");
    }

    @Test
    @DisplayName("Security: Customer A viewing Customer B account throws 403 AccessDeniedException")
    void customerA_viewAccountB_throwsAccessDenied() {
        authenticate("cust-A", "ROLE_CUSTOMER");

        AccountMaster accountB = AccountMaster.builder()
                .accountId("acct-B")
                .customerId("cust-B")
                .accountStatus("ACTIVE")
                .balanceAmount(new BigDecimal("1000.0000"))
                .currencyCode("PHP")
                .build();

        when(accountRepository.findById("acct-B")).thenReturn(Optional.of(accountB));

        assertThatThrownBy(() -> accountService.getAccount("acct-B"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cust-B");
    }

    @Test
    @DisplayName("Security: Customer A creating account for Customer B throws 403 AccessDeniedException")
    void customerA_createAccountForCustomerB_throwsAccessDenied() {
        authenticate("cust-A", "ROLE_CUSTOMER");

        CreateAccountRequest request = new CreateAccountRequest(
                "cust-B",
                "SAVINGS",
                "PHP"
        );

        assertThatThrownBy(() -> accountService.createAccount(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cust-B");
    }

    @Test
    @DisplayName("Security: Admin can debit Customer B account successfully (privileged bypass)")
    void admin_debitCustomerB_succeeds() {
        authenticate("admin-user", "ROLE_ADMIN");

        AccountMaster accountB = AccountMaster.builder()
                .accountId("acct-B")
                .customerId("cust-B")
                .accountStatus("ACTIVE")
                .balanceAmount(new BigDecimal("1000.0000"))
                .currencyCode("PHP")
                .build();

        when(accountRepository.findByIdForUpdate("acct-B")).thenReturn(Optional.of(accountB));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = accountService.debit("acct-B", new BigDecimal("100.0000"), "txn-1", "WITHDRAWAL");
        assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("900.0000"));
        verify(outboxMasterRepository).save(any());
    }

    @Test
    @DisplayName("Outbox Atomicity: saveOutbox failure throws RuntimeException, preventing silent swallow")
    void saveOutbox_failure_throwsRuntimeException() {
        authenticate("cust-B", "ROLE_CUSTOMER");

        AccountMaster accountB = AccountMaster.builder()
                .accountId("acct-B")
                .customerId("cust-B")
                .accountStatus("ACTIVE")
                .balanceAmount(new BigDecimal("1000.0000"))
                .currencyCode("PHP")
                .build();

        when(accountRepository.findByIdForUpdate("acct-B")).thenReturn(Optional.of(accountB));
        when(outboxMasterRepository.save(any()))
                .thenThrow(new RuntimeException("Oracle OUTBOX_MASTER tablespace full"));

        assertThatThrownBy(() ->
                accountService.debit("acct-B", new BigDecimal("100.0000"), "txn-1", "WITHDRAWAL")
        ).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to persist outbox event");
    }
}
