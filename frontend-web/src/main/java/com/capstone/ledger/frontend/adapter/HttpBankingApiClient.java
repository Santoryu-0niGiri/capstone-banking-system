package com.capstone.ledger.frontend.adapter;

import com.capstone.ledger.frontend.adapter.dto.ApiResponseDto;
import com.capstone.ledger.frontend.adapter.dto.BackendDtos.*;
import com.capstone.ledger.frontend.form.RegisterForm;
import com.capstone.ledger.frontend.form.TransactionForm;
import com.capstone.ledger.frontend.model.*;
import com.capstone.ledger.frontend.model.enums.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Live HTTP client implementation of BankingApiClient.
 * Communicates directly with Spring Cloud Gateway (default http://localhost:8080).
 * Translates backend JSON responses into Frontend View Models (Anti-Corruption Layer).
 */
@Service
@ConditionalOnProperty(name = "banking.backend.mode", havingValue = "gateway")
public class HttpBankingApiClient implements BankingApiClient {

    private final RestClient restClient;
    private final String gatewayUrl;

    public HttpBankingApiClient(@Value("${banking.backend.gateway-url:http://localhost:8080}") String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        this.restClient = RestClient.builder()
                .baseUrl(gatewayUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<UserSession> authenticate(String email, String password) {
        try {
            ApiResponseDto<LoginRes> response = restClient.post()
                    .uri("/api/auth/login")
                    .body(new LoginReq(email, password))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<LoginRes>>() {});

            if (response != null && response.isSuccess() && response.getData() != null) {
                LoginRes res = response.getData();
                UserRole role = email.toLowerCase().contains("admin") ? UserRole.ADMIN : UserRole.CUSTOMER;
                UserSession session = new UserSession(
                        res.customerId(), res.customerId(), res.email(), res.email(),
                        res.email(), role, res.token()
                );
                return Optional.of(session);
            }
        } catch (Exception ex) {
            // Log warning or return empty on invalid credentials
        }
        return Optional.empty();
    }

    @Override
    public CustomerView registerCustomer(RegisterForm form) {
        RegisterReq req = new RegisterReq(
                form.getFirstName(), form.getLastName(), form.getEmail(),
                form.getContactNo(), form.getBirthDate(), form.getPassword()
        );

        ApiResponseDto<RegisterRes> response = restClient.post()
                .uri("/api/auth/register")
                .body(req)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponseDto<RegisterRes>>() {});

        if (response != null && response.isSuccess() && response.getData() != null) {
            RegisterRes res = response.getData();
            CustomerView cust = new CustomerView(
                    res.customerId(), res.firstName(), res.lastName(), res.email(),
                    form.getContactNo(), form.getBirthDate(), form.getAddress(),
                    form.getIdType(), form.getIdNumber(), KycStatus.PENDING_VERIFICATION,
                    UserRole.CUSTOMER, LocalDateTime.now()
            );

            // Automatically open initial account if requested
            try {
                openAccount(res.customerId(), form.getInitialAccountType(), form.getCurrencyCode(), BigDecimal.ZERO);
            } catch (Exception ignored) {}

            return cust;
        }
        throw new IllegalStateException(response != null ? response.getMessage() : "Registration failed at API gateway");
    }

    @Override
    public Optional<CustomerView> getCustomerById(String customerId) {
        // Reads accounts and constructs customer view
        List<AccountView> accounts = getAccountsByCustomerId(customerId);
        CustomerView view = new CustomerView();
        view.setCustomerId(customerId);
        view.setAccounts(accounts);
        return Optional.of(view);
    }

    @Override
    public List<CustomerView> getAllCustomers() {
        return Collections.emptyList();
    }

    @Override
    public void updateKycStatus(String customerId, KycStatus status) {
        // Hook for future Admin KYC service endpoint
    }

    @Override
    public Optional<AccountView> getAccountById(String accountId) {
        try {
            ApiResponseDto<AccountRes> response = restClient.get()
                    .uri("/api/accounts/{id}", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<AccountRes>>() {});

            if (response != null && response.getData() != null) {
                return Optional.of(mapAccount(response.getData()));
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override
    public List<AccountView> getAccountsByCustomerId(String customerId) {
        try {
            ApiResponseDto<List<AccountRes>> response = restClient.get()
                    .uri("/api/accounts/customer/{id}", customerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<List<AccountRes>>>() {});

            if (response != null && response.getData() != null) {
                return response.getData().stream().map(this::mapAccount).toList();
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    @Override
    public List<AccountView> getAllAccounts() {
        return Collections.emptyList();
    }

    @Override
    public AccountView openAccount(String customerId, AccountType type, String currencyCode, BigDecimal initialDeposit) {
        CreateAccountReq req = new CreateAccountReq(
                customerId,
                type != null ? type.name() : "SAVINGS",
                currencyCode != null ? currencyCode : "PHP"
        );

        ApiResponseDto<AccountRes> response = restClient.post()
                .uri("/api/accounts")
                .body(req)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponseDto<AccountRes>>() {});

        if (response != null && response.getData() != null) {
            AccountView acct = mapAccount(response.getData());
            if (initialDeposit != null && initialDeposit.compareTo(BigDecimal.ZERO) > 0) {
                TransactionForm tf = new TransactionForm();
                tf.setFromAcctNo(acct.getAccountId());
                tf.setTxnType(TransactionType.DEPOSIT);
                tf.setAmount(initialDeposit);
                executeTransaction(tf);
            }
            return acct;
        }
        throw new IllegalStateException("Failed to create account at API gateway");
    }

    @Override
    public void updateAccountStatus(String accountId, AccountStatus status) {
        // Hook for accounts-service admin endpoint
    }

    @Override
    public List<TransactionView> executeTransaction(TransactionForm form) {
        MutateReq req = new MutateReq(
                form.getFromAcctNo(),
                form.getToAcctNo(),
                form.getTxnType().name(),
                form.getAmount(),
                form.getIdempotencyKey()
        );

        ApiResponseDto<MutateRes> response = restClient.post()
                .uri("/api/v1/ledger/mutate")
                .body(req)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponseDto<MutateRes>>() {});

        if (response != null && response.getData() != null) {
            MutateRes res = response.getData();
            TransactionView tv = new TransactionView();
            tv.setTxnId(res.txnId());
            tv.setMutationId("MUT-" + res.txnId().substring(0, 8));
            tv.setAccountId(res.accountId());
            tv.setCounterpartyAccountId(form.getToAcctNo());
            tv.setTxnType(form.getTxnType());
            tv.setDirection(form.getTxnType() == TransactionType.DEPOSIT ? MutationDirection.CREDIT : MutationDirection.DEBIT);
            tv.setAmount(res.amount());
            tv.setAuditState(AuditState.COMMITTED);
            tv.setTimestamp(LocalDateTime.now());
            return List.of(tv);
        }
        throw new IllegalStateException("Ledger mutation failed: " + (response != null ? response.getMessage() : ""));
    }

    @Override
    public List<TransactionView> getTransactionsByAccountId(String accountId) {
        return Collections.emptyList();
    }

    @Override
    public List<TransactionView> getAllTransactions() {
        return Collections.emptyList();
    }

    @Override
    public Optional<TransactionView> getTransactionById(String txnId) {
        return Optional.empty();
    }

    @Override
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return BigDecimal.ONE;
        if ("USD".equalsIgnoreCase(fromCurrency) && "PHP".equalsIgnoreCase(toCurrency)) return new BigDecimal("56.50");
        if ("PHP".equalsIgnoreCase(fromCurrency) && "USD".equalsIgnoreCase(toCurrency)) return new BigDecimal("0.0177");
        if ("EUR".equalsIgnoreCase(fromCurrency) && "PHP".equalsIgnoreCase(toCurrency)) return new BigDecimal("61.20");
        return BigDecimal.ONE;
    }

    @Override
    public List<NotificationView> getNotificationsByCustomerId(String customerId) {
        return Collections.emptyList();
    }

    @Override
    public void markNotificationAsRead(String notifId) {
    }

    @Override
    public List<ReconciliationRunView> getReconciliationRuns() {
        return Collections.emptyList();
    }

    @Override
    public void triggerReconciliationRun() {
    }

    private AccountView mapAccount(AccountRes res) {
        AccountType type;
        try {
            type = AccountType.valueOf(res.accountType().toUpperCase());
        } catch (Exception e) {
            type = AccountType.SAVINGS;
        }

        AccountStatus status;
        try {
            status = AccountStatus.valueOf(res.accountStatus().toUpperCase());
        } catch (Exception e) {
            status = AccountStatus.ACTIVE;
        }

        return new AccountView(
                res.accountId(),
                res.customerId(),
                type,
                res.currencyCode() != null ? res.currencyCode() : "PHP",
                status,
                res.balanceAmount() != null ? res.balanceAmount() : BigDecimal.ZERO,
                res.createdAt() != null ? res.createdAt() : LocalDateTime.now()
        );
    }
}

