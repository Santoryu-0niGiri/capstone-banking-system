package com.capstone.accounts.controller;

import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.ApiResponse;
import com.capstone.common.dto.CreateAccountRequest;
import com.capstone.accounts.service.AccountService;
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
    public ResponseEntity<ApiResponse<AccountDTO>> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountDTO dto = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Account created", dto));
    }

    @GetMapping("/{acctNo}")
    public ResponseEntity<ApiResponse<AccountDTO>> getAccount(@PathVariable Long acctNo) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccount(acctNo)));
    }

    @GetMapping("/{acctNo}/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(@PathVariable Long acctNo) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getBalance(acctNo)));
    }

    @GetMapping("/customer/{custId}")
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getAccountsForCustomer(@PathVariable Long custId) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccountsForCustomer(custId)));
    }
}
