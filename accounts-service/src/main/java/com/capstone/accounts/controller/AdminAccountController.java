package com.capstone.accounts.controller;

import com.capstone.accounts.service.AccountService;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only account governance endpoints.
 *
 * Routed via API Gateway at /api/admin/accounts/**  (requires ROLE_ADMIN JWT claim).
 *
 * freeze/unfreeze → updates ACCOUNT_MASTER.account_status (FROZEN | ACTIVE)
 * Any subsequent mutation on a FROZEN account is rejected by TransactionService
 * with 409 (account is not ACTIVE).
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountService accountService;

    /**
     * List every account across all customers.
     * Supports optional ?customerId= filter for the admin customer-drill-down view.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDTO>>> listAllAccounts(
            @RequestParam(name = "customerId", required = false) String customerId) {

        List<AccountDTO> accounts = (customerId != null && !customerId.isBlank())
                ? accountService.getAccountsForCustomer(customerId)
                : accountService.getAllAccounts();

        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    /**
     * Update account status.
     * Allowed values: ACTIVE (unfreeze), FROZEN (freeze), CLOSED.
     *
     * @param accountId target account UUID
     * @param status    new account_status value
     */
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<ApiResponse<AccountDTO>> updateAccountStatus(
            @PathVariable("accountId") String accountId,
            @RequestParam("status") String status) {

        AccountDTO updated = accountService.updateAccountStatus(accountId, status);
        return ResponseEntity.ok(
                ApiResponse.ok("Account " + accountId + " status updated to " + status, updated));
    }
}
