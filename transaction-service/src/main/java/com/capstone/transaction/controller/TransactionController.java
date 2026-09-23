
package com.capstone.transaction.controller;

import com.capstone.common.dto.ApiResponse;
import com.capstone.common.dto.TransactionRequest;
import com.capstone.common.dto.TransactionResponse;
import com.capstone.common.exception.ResourceNotFoundException;
import com.capstone.transaction.entity.postgres.LedgerMutationAudit;
import com.capstone.transaction.service.TransactionService;
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

import java.util.List;

/**
 * Ledger mutation endpoints.
 * txnType in TransactionRequest drives routing to withdraw/deposit/transfer.
 * txnId path variable is a String UUID matching TRANSACTION_MASTER.txn_id.
 */
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Single entry point: client sets request.txnType to WITHDRAWAL|DEPOSIT|TRANSFER.
     * The service layer enforces the DDL constraints on which account IDs must
     * be populated for each type.
     */
    @PostMapping("/mutate")
    public ResponseEntity<ApiResponse<TransactionResponse>> mutate(
            @Valid @RequestBody TransactionRequest request) {

        TransactionResponse response = switch (request.txnType()) {
            case "WITHDRAWAL" -> transactionService.withdraw(request);
            case "DEPOSIT"    -> transactionService.deposit(request);
            case "TRANSFER"   -> transactionService.transfer(request);
            default -> throw new IllegalArgumentException(
                    "Unsupported txnType: " + request.txnType());
        };
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(request.txnType() + " processed", response));
    }

    /**
     * Returns all ledger_mutation_audit rows for a given transaction.
     * WITHDRAWAL → 1 row (DEBIT), DEPOSIT → 1 row (CREDIT),
     * TRANSFER   → 2 rows (DEBIT + CREDIT).
     */
    @GetMapping("/audit/{txnId}")
    public ResponseEntity<ApiResponse<List<LedgerMutationAudit>>> getAudit(
            @PathVariable String txnId) {
        List<LedgerMutationAudit> audits = transactionService.findAuditByTxnId(txnId);
        if (audits.isEmpty()) {
            throw new ResourceNotFoundException("Transaction " + txnId + " not found");
        }
        return ResponseEntity.ok(ApiResponse.ok(audits));
    }
}

