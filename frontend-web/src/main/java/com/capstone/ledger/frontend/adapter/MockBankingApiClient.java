package com.capstone.ledger.frontend.adapter;

import com.capstone.ledger.frontend.form.RegisterForm;
import com.capstone.ledger.frontend.form.TransactionForm;
import com.capstone.ledger.frontend.model.*;
import com.capstone.ledger.frontend.model.enums.*;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stateful, in-memory implementation of BankingApiClient.
 * Enables zero-dependency frontend testing, local UI iteration, and presentation demos
 * without requiring Docker containers or backend microservices to be running.
 */
@Service
@ConditionalOnProperty(name = "banking.backend.mode", havingValue = "mock", matchIfMissing = true)
public class MockBankingApiClient implements BankingApiClient {

    private final Map<String, CustomerView> customersById = new ConcurrentHashMap<>();
    private final Map<String, UserSession> usersByEmail = new ConcurrentHashMap<>();
    private final Map<String, String> passwordsByEmail = new ConcurrentHashMap<>();
    private final Map<String, AccountView> accountsById = new ConcurrentHashMap<>();
    private final Map<String, TransactionView> transactionsById = new ConcurrentHashMap<>();
    private final Map<String, NotificationView> notificationsById = new ConcurrentHashMap<>();
    private final List<ReconciliationRunView> reconciliationRuns = new ArrayList<>();

    // Mock FX rates against PHP
    private final Map<String, BigDecimal> fxRatesToPhp = new HashMap<>();

    private final AtomicInteger custSeq = new AtomicInteger(1002);
    private final AtomicInteger acctSeq = new AtomicInteger(5005);
    private final AtomicInteger txnSeq = new AtomicInteger(8010);

    @PostConstruct
    public void seed() {
        // 1. Initialize FX Rates (e.g. Frankfurter / ECB daily sample rates)
        fxRatesToPhp.put("PHP", BigDecimal.ONE);
        fxRatesToPhp.put("USD", new BigDecimal("56.50"));
        fxRatesToPhp.put("EUR", new BigDecimal("61.20"));
        fxRatesToPhp.put("GBP", new BigDecimal("72.80"));
        fxRatesToPhp.put("SGD", new BigDecimal("42.30"));
        fxRatesToPhp.put("JPY", new BigDecimal("0.38"));

        // 2. Seed Admin User
        UserSession adminSession = new UserSession(
                "USER-ADMIN-01", "CUST-ADMIN", "admin@ledgerbank.com",
                "System Administrator", "admin@ledgerbank.com", UserRole.ADMIN, "mock-jwt-admin-token"
        );
        usersByEmail.put(adminSession.getEmail(), adminSession);
        passwordsByEmail.put(adminSession.getEmail(), "admin123");

        // 3. Seed Customer Juan Dela Cruz
        CustomerView juan = new CustomerView(
                "CUST-1001", "Juan", "Dela Cruz", "juan.delacruz@example.com",
                "+63 917 555 0101", LocalDate.of(1993, 4, 15),
                "45 Ayala Avenue, Makati City, Metro Manila", "PASSPORT", "P3819204A",
                KycStatus.VERIFIED, UserRole.CUSTOMER, LocalDateTime.now().minusMonths(8)
        );
        customersById.put(juan.getCustomerId(), juan);
        passwordsByEmail.put(juan.getEmail(), "password123");
        usersByEmail.put(juan.getEmail(), new UserSession(
                "USER-1001", juan.getCustomerId(), juan.getEmail(), juan.getFullName(),
                juan.getEmail(), UserRole.CUSTOMER, "mock-jwt-juan-token"
        ));

        // 4. Seed Customer Maria Santos
        CustomerView maria = new CustomerView(
                "CUST-1002", "Maria", "Santos", "maria.santos@example.com",
                "+63 918 555 0202", LocalDate.of(1990, 8, 22),
                "88 Ortigas Center, Pasig City, Metro Manila", "NATIONAL_ID", "PH-8102-4910",
                KycStatus.VERIFIED, UserRole.CUSTOMER, LocalDateTime.now().minusMonths(4)
        );
        customersById.put(maria.getCustomerId(), maria);
        passwordsByEmail.put(maria.getEmail(), "password123");
        usersByEmail.put(maria.getEmail(), new UserSession(
                "USER-1002", maria.getCustomerId(), maria.getEmail(), maria.getFullName(),
                maria.getEmail(), UserRole.CUSTOMER, "mock-jwt-maria-token"
        ));

        // 5. Seed Accounts
        AccountView juanSavings = new AccountView(
                "ACCT-1001-PHP", juan.getCustomerId(), AccountType.SAVINGS, "PHP",
                AccountStatus.ACTIVE, new BigDecimal("45250.00"), LocalDateTime.now().minusMonths(8)
        );
        AccountView juanUsd = new AccountView(
                "ACCT-1002-USD", juan.getCustomerId(), AccountType.CHECKING, "USD",
                AccountStatus.ACTIVE, new BigDecimal("1500.00"), LocalDateTime.now().minusMonths(5)
        );

        AccountView mariaSavings = new AccountView(
                "ACCT-2001-PHP", maria.getCustomerId(), AccountType.SAVINGS, "PHP",
                AccountStatus.ACTIVE, new BigDecimal("128400.00"), LocalDateTime.now().minusMonths(4)
        );
        AccountView mariaWallet = new AccountView(
                "ACCT-2002-PHP", maria.getCustomerId(), AccountType.WALLET, "PHP",
                AccountStatus.ACTIVE, new BigDecimal("4750.50"), LocalDateTime.now().minusMonths(2)
        );
        AccountView mariaEur = new AccountView(
                "ACCT-2003-EUR", maria.getCustomerId(), AccountType.SAVINGS, "EUR",
                AccountStatus.ACTIVE, new BigDecimal("3200.00"), LocalDateTime.now().minusMonths(1)
        );

        accountsById.put(juanSavings.getAccountId(), juanSavings);
        accountsById.put(juanUsd.getAccountId(), juanUsd);
        accountsById.put(mariaSavings.getAccountId(), mariaSavings);
        accountsById.put(mariaWallet.getAccountId(), mariaWallet);
        accountsById.put(mariaEur.getAccountId(), mariaEur);

        juan.getAccounts().add(juanSavings);
        juan.getAccounts().add(juanUsd);
        maria.getAccounts().add(mariaSavings);
        maria.getAccounts().add(mariaWallet);
        maria.getAccounts().add(mariaEur);

        // 6. Seed Transactions
        seedTxn("TXN-7001", "MUT-8001", juanSavings.getAccountId(), null, TransactionType.DEPOSIT,
                MutationDirection.CREDIT, new BigDecimal("50000.00"), "PHP", false, null, null, null,
                LocalDateTime.now().minusDays(15));
        seedTxn("TXN-7002", "MUT-8002", juanSavings.getAccountId(), null, TransactionType.WITHDRAWAL,
                MutationDirection.DEBIT, new BigDecimal("4750.00"), "PHP", false, null, null, null,
                LocalDateTime.now().minusDays(10));
        // Cross-currency transfer: Maria sent PHP to Juan's USD account
        seedTxn("TXN-7003", "MUT-8003", mariaSavings.getAccountId(), juanUsd.getAccountId(), TransactionType.TRANSFER,
                MutationDirection.DEBIT, new BigDecimal("11300.00"), "PHP", true,
                new BigDecimal("0.0177"), new BigDecimal("200.00"), "USD",
                LocalDateTime.now().minusDays(3));
        seedTxn("TXN-7003", "MUT-8004", juanUsd.getAccountId(), mariaSavings.getAccountId(), TransactionType.TRANSFER,
                MutationDirection.CREDIT, new BigDecimal("200.00"), "USD", true,
                new BigDecimal("56.50"), new BigDecimal("11300.00"), "PHP",
                LocalDateTime.now().minusDays(3));

        // 7. Seed Notifications
        seedNotification(juan.getCustomerId(), "Salary credit of ₱50,000.00 posted to ACCT-1001-PHP.", "SMS", LocalDateTime.now().minusDays(15), true);
        seedNotification(juan.getCustomerId(), "Inward remittance of $200.00 received from Maria Santos.", "PUSH", LocalDateTime.now().minusDays(3), false);
        seedNotification(maria.getCustomerId(), "Fund transfer of ₱11,300.00 ($200.00 USD) successful.", "EMAIL", LocalDateTime.now().minusDays(3), true);

        // 8. Seed Reconciliation Run & Audit Results
        ReconciliationRunView run = new ReconciliationRunView(
                "RUN-2026-09-01", LocalDateTime.now().minusHours(6), LocalDateTime.now().minusHours(5).minusMinutes(58),
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), 450, 449, 1, "COMPLETED"
        );
        ReconciliationResultView exception = new ReconciliationResultView(
                "RES-9011", run.getRunId(), "TXN-7002", juanSavings.getAccountId(), "EXCEPTION",
                "POSTING_ROUNDING_DISCREPANCY", new BigDecimal("4750.00"), new BigDecimal("4750.01"),
                new BigDecimal("0.01"), 1, "LOW", LocalDateTime.now().minusHours(5).minusMinutes(58)
        );
        run.getResults().add(exception);
        reconciliationRuns.add(run);
    }

    private void seedTxn(String txnId, String mutationId, String accountId, String counterpartyId,
                         TransactionType type, MutationDirection dir, BigDecimal amount,
                         String currency, boolean cross, BigDecimal rate, BigDecimal destAmt,
                         String destCurr, LocalDateTime time) {
        TransactionView tv = new TransactionView(
                mutationId, txnId, accountId, counterpartyId, type, dir, amount,
                currency, cross, rate, destAmt, destCurr, AuditState.COMMITTED, time
        );
        transactionsById.put(mutationId, tv);
    }

    private void seedNotification(String custId, String msg, String channel, LocalDateTime time, boolean read) {
        String id = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        NotificationView nv = new NotificationView(id, custId, msg, channel, "SENT", time, read);
        notificationsById.put(id, nv);
    }

    @Override
    public Optional<UserSession> authenticate(String email, String password) {
        String storedPass = passwordsByEmail.get(email);
        if (storedPass != null && storedPass.equals(password)) {
            return Optional.ofNullable(usersByEmail.get(email));
        }
        return Optional.empty();
    }

    @Override
    public CustomerView registerCustomer(RegisterForm form) {
        if (usersByEmail.containsKey(form.getEmail())) {
            throw new IllegalArgumentException("An account with email " + form.getEmail() + " already exists.");
        }

        String custId = "CUST-" + custSeq.incrementAndGet();
        CustomerView customer = new CustomerView(
                custId, form.getFirstName(), form.getLastName(), form.getEmail(),
                form.getContactNo(), form.getBirthDate(), form.getAddress(),
                form.getIdType(), form.getIdNumber(), KycStatus.PENDING_VERIFICATION,
                UserRole.CUSTOMER, LocalDateTime.now()
        );

        customersById.put(custId, customer);
        passwordsByEmail.put(form.getEmail(), form.getPassword());

        UserSession session = new UserSession(
                "USER-" + custSeq.get(), custId, form.getEmail(),
                customer.getFullName(), form.getEmail(), UserRole.CUSTOMER, "mock-jwt-" + custId
        );
        usersByEmail.put(form.getEmail(), session);

        // Open initial account for customer
        AccountType initialType = form.getInitialAccountType() != null ? form.getInitialAccountType() : AccountType.SAVINGS;
        String initialCurrency = form.getCurrencyCode() != null ? form.getCurrencyCode() : "PHP";
        openAccount(custId, initialType, initialCurrency, BigDecimal.ZERO);

        seedNotification(custId, "Welcome to Core Retail Ledger! Your registration is received and under verification.", "EMAIL", LocalDateTime.now(), false);

        return customer;
    }

    @Override
    public Optional<CustomerView> getCustomerById(String customerId) {
        CustomerView customer = customersById.get(customerId);
        if (customer != null) {
            customer.setAccounts(getAccountsByCustomerId(customerId));
        }
        return Optional.ofNullable(customer);
    }

    @Override
    public List<CustomerView> getAllCustomers() {
        List<CustomerView> list = new ArrayList<>(customersById.values());
        list.forEach(c -> c.setAccounts(getAccountsByCustomerId(c.getCustomerId())));
        return list;
    }

    @Override
    public void updateKycStatus(String customerId, KycStatus status) {
        CustomerView cust = customersById.get(customerId);
        if (cust != null) {
            cust.setKycStatus(status);
            seedNotification(customerId, "Your KYC verification status has been updated to: " + status.getDisplayName(), "SMS", LocalDateTime.now(), false);
        }
    }

    @Override
    public Optional<AccountView> getAccountById(String accountId) {
        return Optional.ofNullable(accountsById.get(accountId));
    }

    @Override
    public List<AccountView> getAccountsByCustomerId(String customerId) {
        return accountsById.values().stream()
                .filter(a -> a.getCustomerId().equals(customerId))
                .sorted(Comparator.comparing(AccountView::getCreatedAt))
                .toList();
    }

    @Override
    public List<AccountView> getAllAccounts() {
        return new ArrayList<>(accountsById.values());
    }

    @Override
    public AccountView openAccount(String customerId, AccountType type, String currencyCode, BigDecimal initialDeposit) {
        CustomerView customer = customersById.get(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Customer " + customerId + " not found.");
        }

        String curr = currencyCode != null ? currencyCode.toUpperCase() : "PHP";
        String acctId = "ACCT-" + acctSeq.incrementAndGet() + "-" + curr;
        BigDecimal initialBal = initialDeposit != null ? initialDeposit : BigDecimal.ZERO;

        AccountView account = new AccountView(
                acctId, customerId, type, curr, AccountStatus.ACTIVE, initialBal, LocalDateTime.now()
        );

        accountsById.put(acctId, account);
        customer.getAccounts().add(account);

        if (initialBal.compareTo(BigDecimal.ZERO) > 0) {
            seedTxn("TXN-" + txnSeq.incrementAndGet(), "MUT-" + System.currentTimeMillis() % 100000,
                    acctId, null, TransactionType.DEPOSIT, MutationDirection.CREDIT,
                    initialBal, curr, false, null, null, null, LocalDateTime.now());
        }

        seedNotification(customerId, "New " + type.getDisplayName() + " (" + acctId + ") opened successfully.", "SMS", LocalDateTime.now(), false);
        return account;
    }

    @Override
    public void updateAccountStatus(String accountId, AccountStatus status) {
        AccountView acct = accountsById.get(accountId);
        if (acct != null) {
            acct.setAccountStatus(status);
            seedNotification(acct.getCustomerId(), "Security Notice: Account " + accountId + " status is now " + status.getDisplayName(), "SMS", LocalDateTime.now(), false);
        }
    }

    @Override
    public synchronized List<TransactionView> executeTransaction(TransactionForm form) {
        AccountView fromAcct = accountsById.get(form.getFromAcctNo());
        if (fromAcct == null) {
            throw new IllegalArgumentException("Source account " + form.getFromAcctNo() + " not found.");
        }
        if (fromAcct.getAccountStatus() == AccountStatus.FROZEN) {
            throw new IllegalStateException("Source account " + form.getFromAcctNo() + " is FROZEN. Balance mutation rejected.");
        }
        if (fromAcct.getAccountStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Source account " + form.getFromAcctNo() + " is CLOSED.");
        }

        BigDecimal amount = form.getAmount();
        String txnId = "TXN-" + txnSeq.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();

        List<TransactionView> results = new ArrayList<>();

        switch (form.getTxnType()) {
            case DEPOSIT -> {
                fromAcct.setBalance(fromAcct.getBalance().add(amount));
                String mutId = "MUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                TransactionView tv = new TransactionView(
                        mutId, txnId, fromAcct.getAccountId(), null, TransactionType.DEPOSIT,
                        MutationDirection.CREDIT, amount, fromAcct.getCurrencyCode(),
                        false, null, null, null, AuditState.COMMITTED, now
                );
                transactionsById.put(mutId, tv);
                results.add(tv);
                seedNotification(fromAcct.getCustomerId(), "Deposit of " + fromAcct.getCurrencySymbol() + amount + " posted to " + fromAcct.getAccountId(), "SMS", now, false);
            }
            case WITHDRAWAL -> {
                if (fromAcct.getBalance().compareTo(amount) < 0) {
                    throw new IllegalArgumentException("Insufficient funds in account " + fromAcct.getAccountId() + ". Available: " + fromAcct.getFormattedBalance());
                }
                fromAcct.setBalance(fromAcct.getBalance().subtract(amount));
                String mutId = "MUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                TransactionView tv = new TransactionView(
                        mutId, txnId, fromAcct.getAccountId(), null, TransactionType.WITHDRAWAL,
                        MutationDirection.DEBIT, amount, fromAcct.getCurrencyCode(),
                        false, null, null, null, AuditState.COMMITTED, now
                );
                transactionsById.put(mutId, tv);
                results.add(tv);
                seedNotification(fromAcct.getCustomerId(), "Withdrawal of " + fromAcct.getCurrencySymbol() + amount + " debited from " + fromAcct.getAccountId(), "SMS", now, false);
            }
            case TRANSFER -> {
                AccountView toAcct = accountsById.get(form.getToAcctNo());
                if (toAcct == null) {
                    throw new IllegalArgumentException("Destination account " + form.getToAcctNo() + " not found.");
                }
                if (toAcct.getAccountStatus() == AccountStatus.FROZEN) {
                    throw new IllegalStateException("Destination account " + form.getToAcctNo() + " is FROZEN.");
                }
                if (fromAcct.getAccountId().equalsIgnoreCase(toAcct.getAccountId())) {
                    throw new IllegalArgumentException("Source and destination accounts cannot be the same.");
                }
                if (fromAcct.getBalance().compareTo(amount) < 0) {
                    throw new IllegalArgumentException("Insufficient funds for transfer. Available: " + fromAcct.getFormattedBalance());
                }

                // Check cross currency
                boolean isCross = !fromAcct.getCurrencyCode().equalsIgnoreCase(toAcct.getCurrencyCode());
                BigDecimal rate = BigDecimal.ONE;
                BigDecimal destAmount = amount;

                if (isCross) {
                    rate = getExchangeRate(fromAcct.getCurrencyCode(), toAcct.getCurrencyCode());
                    destAmount = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }

                // Debit source
                fromAcct.setBalance(fromAcct.getBalance().subtract(amount));
                String debitMutId = "MUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                TransactionView debitView = new TransactionView(
                        debitMutId, txnId, fromAcct.getAccountId(), toAcct.getAccountId(), TransactionType.TRANSFER,
                        MutationDirection.DEBIT, amount, fromAcct.getCurrencyCode(),
                        isCross, rate, destAmount, toAcct.getCurrencyCode(), AuditState.COMMITTED, now
                );
                transactionsById.put(debitMutId, debitView);
                results.add(debitView);

                // Credit destination
                toAcct.setBalance(toAcct.getBalance().add(destAmount));
                String creditMutId = "MUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                TransactionView creditView = new TransactionView(
                        creditMutId, txnId, toAcct.getAccountId(), fromAcct.getAccountId(), TransactionType.TRANSFER,
                        MutationDirection.CREDIT, destAmount, toAcct.getCurrencyCode(),
                        isCross, rate, amount, fromAcct.getCurrencyCode(), AuditState.COMMITTED, now
                );
                transactionsById.put(creditMutId, creditView);
                results.add(creditView);

                // Notifications
                seedNotification(fromAcct.getCustomerId(),
                        "Transferred " + fromAcct.getCurrencySymbol() + amount + " to " + toAcct.getAccountId() + (isCross ? " (converted to " + toAcct.getCurrencySymbol() + destAmount + ")" : ""),
                        "SMS", now, false);
                seedNotification(toAcct.getCustomerId(),
                        "Received " + toAcct.getCurrencySymbol() + destAmount + " from " + fromAcct.getAccountId(),
                        "SMS", now, false);
            }
        }

        return results;
    }

    @Override
    public List<TransactionView> getTransactionsByAccountId(String accountId) {
        return transactionsById.values().stream()
                .filter(t -> t.getAccountId().equalsIgnoreCase(accountId))
                .sorted(Comparator.comparing(TransactionView::getTimestamp).reversed())
                .toList();
    }

    @Override
    public List<TransactionView> getAllTransactions() {
        return transactionsById.values().stream()
                .sorted(Comparator.comparing(TransactionView::getTimestamp).reversed())
                .toList();
    }

    @Override
    public Optional<TransactionView> getTransactionById(String txnId) {
        return transactionsById.values().stream()
                .filter(t -> t.getTxnId().equalsIgnoreCase(txnId))
                .findFirst();
    }

    @Override
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return BigDecimal.ONE;

        BigDecimal fromRateToPhp = fxRatesToPhp.getOrDefault(fromCurrency.toUpperCase(), BigDecimal.ONE);
        BigDecimal toRateToPhp = fxRatesToPhp.getOrDefault(toCurrency.toUpperCase(), BigDecimal.ONE);

        // e.g. USD to PHP: 56.50 / 1 = 56.50
        // e.g. PHP to USD: 1 / 56.50 = 0.0177
        // e.g. EUR to USD: 61.20 / 56.50 = 1.083
        return fromRateToPhp.divide(toRateToPhp, 6, RoundingMode.HALF_UP);
    }

    @Override
    public List<NotificationView> getNotificationsByCustomerId(String customerId) {
        return notificationsById.values().stream()
                .filter(n -> n.getCustId().equals(customerId))
                .sorted(Comparator.comparing(NotificationView::getSentAt).reversed())
                .toList();
    }

    @Override
    public void markNotificationAsRead(String notifId) {
        NotificationView n = notificationsById.get(notifId);
        if (n != null) {
            n.setRead(true);
        }
    }

    @Override
    public List<ReconciliationRunView> getReconciliationRuns() {
        return reconciliationRuns;
    }

    @Override
    public void triggerReconciliationRun() {
        ReconciliationRunView newRun = new ReconciliationRunView(
                "RUN-" + System.currentTimeMillis() % 100000,
                LocalDateTime.now().minusMinutes(2), LocalDateTime.now(),
                LocalDateTime.now().minusHours(12), LocalDateTime.now(),
                transactionsById.size(), transactionsById.size(), 0, "COMPLETED"
        );
        reconciliationRuns.add(0, newRun);
    }
}

