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

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/debit")
    public ResponseEntity<ApiResponse<TransactionResponse>> debit(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.debit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Debit processed", response));
    }

    @PostMapping("/credit")
    public ResponseEntity<ApiResponse<TransactionResponse>> credit(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.credit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Credit processed", response));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Transfer processed", response));
    }

    @GetMapping("/{txnId}")
    public ResponseEntity<ApiResponse<LedgerMutationAudit>> getTransaction(@PathVariable UUID txnId) {
        LedgerMutationAudit audit = transactionService.findAuditByTxnId(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + txnId + " not found"));
        return ResponseEntity.ok(ApiResponse.ok(audit));
    }
}
