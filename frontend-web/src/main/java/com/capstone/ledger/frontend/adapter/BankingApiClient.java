package com.capstone.ledger.frontend.adapter;

import com.capstone.ledger.frontend.form.RegisterForm;
import com.capstone.ledger.frontend.form.TransactionForm;
import com.capstone.ledger.frontend.model.*;
import com.capstone.ledger.frontend.model.enums.AccountStatus;
import com.capstone.ledger.frontend.model.enums.AccountType;
import com.capstone.ledger.frontend.model.enums.KycStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Anti-Corruption Layer (ACL) boundary interface.
 * All frontend controllers interact EXCLUSIVELY with this interface.
 *
 * Switching between the in-memory mock and the real Spring Cloud API Gateway
 * requires changing only a single configuration property:
 * banking.backend.mode=mock (or gateway).
 */
public interface BankingApiClient {

    // --- Authentication & Identity ---
    Optional<UserSession> authenticate(String email, String password);
    CustomerView registerCustomer(RegisterForm form);

    // --- Customer Domain ---
    Optional<CustomerView> getCustomerById(String customerId);
    List<CustomerView> getAllCustomers();
    void updateKycStatus(String customerId, KycStatus status);

    // --- Accounts Domain ---
    Optional<AccountView> getAccountById(String accountId);
    List<AccountView> getAccountsByCustomerId(String customerId);
    List<AccountView> getAllAccounts();
    AccountView openAccount(String customerId, AccountType type, String currencyCode, BigDecimal initialDeposit);
    void updateAccountStatus(String accountId, AccountStatus status);

    // --- Transactions & Balance Mutation ---
    List<TransactionView> executeTransaction(TransactionForm form);
    List<TransactionView> getTransactionsByAccountId(String accountId);
    List<TransactionView> getAllTransactions();
    Optional<TransactionView> getTransactionById(String txnId);

    // --- ForEx & Cross-Currency ---
    BigDecimal getExchangeRate(String fromCurrency, String toCurrency);

    // --- Alerts & Notifications ---
    List<NotificationView> getNotificationsByCustomerId(String customerId);
    void markNotificationAsRead(String notifId);

    // --- Reconciliation & Audit ---
    List<ReconciliationRunView> getReconciliationRuns();
    void triggerReconciliationRun();
}

