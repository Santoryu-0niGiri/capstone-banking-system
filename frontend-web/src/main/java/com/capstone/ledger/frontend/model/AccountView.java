package com.capstone.ledger.frontend.model;

import com.capstone.ledger.frontend.model.enums.AccountStatus;
import com.capstone.ledger.frontend.model.enums.AccountType;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;

/**
 * Frontend view model for an account, mirroring ACCOUNT_MASTER in the ERD:
 * account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_at.
 */
public class AccountView {

    private String accountId;
    private String customerId;
    private AccountType accountType;
    private String currencyCode;
    private AccountStatus accountStatus;
    private BigDecimal balance;
    private LocalDateTime createdAt;

    public AccountView() {
        this.balance = BigDecimal.ZERO;
        this.currencyCode = "PHP";
        this.accountStatus = AccountStatus.ACTIVE;
    }

    public AccountView(String accountId, String customerId, AccountType accountType,
                       String currencyCode, AccountStatus accountStatus,
                       BigDecimal balance, LocalDateTime createdAt) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.accountType = accountType;
        this.currencyCode = currencyCode != null ? currencyCode : "PHP";
        this.accountStatus = accountStatus != null ? accountStatus : AccountStatus.ACTIVE;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // Currency symbol helper
    public String getCurrencySymbol() {
        if ("USD".equalsIgnoreCase(currencyCode)) return "$";
        if ("EUR".equalsIgnoreCase(currencyCode)) return "€";
        if ("GBP".equalsIgnoreCase(currencyCode)) return "£";
        if ("JPY".equalsIgnoreCase(currencyCode)) return "¥";
        if ("SGD".equalsIgnoreCase(currencyCode)) return "S$";
        return "₱"; // Default PHP
    }

    public String getFormattedBalance() {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return getCurrencySymbol() + df.format(balance != null ? balance : BigDecimal.ZERO);
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public boolean isFrozen() {
        return accountStatus == AccountStatus.FROZEN;
    }

    // --- Aliases for compatibility ---
    public String getAcctNo() { return accountId; }
    public void setAcctNo(String acctNo) { this.accountId = acctNo; }

    public String getCustId() { return customerId; }
    public void setCustId(String custId) { this.customerId = custId; }

    public AccountType getAcctType() { return accountType; }
    public void setAcctType(AccountType acctType) { this.accountType = accountType; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public AccountStatus getAcctStatus() { return accountStatus; }
    public void setAcctStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }

    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }

    // --- Standard Getters & Setters ---
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
