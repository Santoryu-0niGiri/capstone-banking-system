package com.capstone.ledger.frontend.form;

import com.capstone.ledger.frontend.model.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Form for opening a new customer account.
 */
public class OpenAccountForm {

    private String customerId;

    @NotNull(message = "Account type is required")
    private AccountType accountType = AccountType.SAVINGS;

    @NotBlank(message = "Currency code is required")
    private String currencyCode = "PHP";

    @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
    private BigDecimal initialDeposit = BigDecimal.ZERO;

    public OpenAccountForm() {
    }

    public OpenAccountForm(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getInitialDeposit() { return initialDeposit; }
    public void setInitialDeposit(BigDecimal initialDeposit) { this.initialDeposit = initialDeposit; }
}

