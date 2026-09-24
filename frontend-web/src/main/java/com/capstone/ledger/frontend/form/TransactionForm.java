package com.capstone.ledger.frontend.form;

import com.capstone.ledger.frontend.model.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Unified form backing Deposit, Withdrawal, and Fund Transfer.
 * Includes Idempotency Key support and cross-currency detection.
 */
public class TransactionForm {

    @NotNull(message = "Select a transaction operation")
    private TransactionType txnType = TransactionType.TRANSFER;

    private String fromAcctNo;

    // Required only for TRANSFER
    private String toAcctNo;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    private String idempotencyKey;

    private String description;

    public TransactionForm() {
        this.idempotencyKey = "TXN-KEY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // --- Aliases for compatibility ---
    public String getAccountId() { return fromAcctNo; }
    public void setAccountId(String accountId) { this.fromAcctNo = accountId; }

    public String getCounterpartyAccountId() { return toAcctNo; }
    public void setCounterpartyAccountId(String counterpartyAccountId) { this.toAcctNo = counterpartyAccountId; }

    // --- Getters & Setters ---
    public TransactionType getTxnType() { return txnType; }
    public void setTxnType(TransactionType txnType) { this.txnType = txnType; }

    public String getFromAcctNo() { return fromAcctNo; }
    public void setFromAcctNo(String fromAcctNo) { this.fromAcctNo = fromAcctNo; }

    public String getToAcctNo() { return toAcctNo; }
    public void setToAcctNo(String toAcctNo) { this.toAcctNo = toAcctNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
