package com.capstone.accounts.controller;

import com.capstone.accounts.service.AccountService;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.ApiResponse;
import com.capstone.common.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDTO>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        AccountDTO dto = accountService.createAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created", dto));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountDTO>> getAccount(
            @PathVariable("accountId") String accountId) {

        return ResponseEntity.ok(
                ApiResponse.ok(accountService.getAccount(accountId))
        );
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(
            @PathVariable("accountId") String accountId) {

        return ResponseEntity.ok(
                ApiResponse.ok(accountService.getBalance(accountId))
        );
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getAccountsForCustomer(
            @PathVariable("customerId") String customerId) {

        return ResponseEntity.ok(
                ApiResponse.ok(accountService.getAccountsForCustomer(customerId))
        );
    }
}
