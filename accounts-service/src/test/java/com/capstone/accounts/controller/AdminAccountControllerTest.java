package com.capstone.accounts.controller;

import com.capstone.accounts.service.AccountService;
import com.capstone.common.dto.AccountDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AdminAccountController adminAccountController;

    private final AccountDTO sampleAccount = new AccountDTO(
            "acct-1",
            "cust-1",
            "SAVINGS",
            "ACTIVE",
            new BigDecimal("1000.0000"),
            "PHP",
            LocalDateTime.now()
    );

    @Test
    @DisplayName("listAllAccounts with no filter should return all accounts")
    void listAllAccounts_noFilter() {
        when(accountService.getAllAccounts()).thenReturn(List.of(sampleAccount));

        var response = adminAccountController.listAllAccounts(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).hasSize(1);
        verify(accountService).getAllAccounts();
    }

    @Test
    @DisplayName("listAllAccounts with customerId filter should filter by customer")
    void listAllAccounts_withFilter() {
        when(accountService.getAccountsForCustomer("cust-1")).thenReturn(List.of(sampleAccount));

        var response = adminAccountController.listAllAccounts("cust-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).hasSize(1);
        verify(accountService).getAccountsForCustomer("cust-1");
    }

    @Test
    @DisplayName("updateAccountStatus to FROZEN should invoke service and return updated DTO")
    void updateAccountStatus_freeze() {
        AccountDTO frozen = new AccountDTO(
                "acct-1",
                "cust-1",
                "SAVINGS",
                "FROZEN",
                new BigDecimal("1000.0000"),
                "PHP",
                LocalDateTime.now()
        );
        when(accountService.updateAccountStatus("acct-1", "FROZEN")).thenReturn(frozen);

        var response = adminAccountController.updateAccountStatus("acct-1", "FROZEN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().accountStatus()).isEqualTo("FROZEN");
        verify(accountService).updateAccountStatus("acct-1", "FROZEN");
    }
}
