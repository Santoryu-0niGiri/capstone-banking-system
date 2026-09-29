package com.capstone.ledger.frontend.adapter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.capstone.ledger.frontend.adapter.dto.ApiResponseDto;
import com.capstone.ledger.frontend.adapter.dto.BackendDtos.*;
import com.capstone.ledger.frontend.form.RegisterForm;
import com.capstone.ledger.frontend.form.TransactionForm;
import com.capstone.ledger.frontend.model.*;
import com.capstone.ledger.frontend.model.enums.*;
import com.capstone.ledger.frontend.config.SessionAuthInterceptor;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
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

    private static final Logger log = LoggerFactory.getLogger(HttpBankingApiClient.class);
    private static final ZoneId BANKING_ZONE = ZoneId.of("Asia/Manila");

    private final RestClient restClient;
    private final String gatewayUrl;
    private final Map<String, KycStatus> kycStatusOverrides = new java.util.concurrent.ConcurrentHashMap<>();

    public HttpBankingApiClient(@Value("${banking.backend.gateway-url:http://localhost:8080}") String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        log.info("Initialized HttpBankingApiClient in LIVE GATEWAY MODE targeting Spring Cloud Gateway at: {}", gatewayUrl);
        this.restClient = RestClient.builder()
                .baseUrl(gatewayUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    String token = resolveBearerToken();
                    if (token != null && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION, token);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    private String resolveBearerToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpSession session = attributes.getRequest().getSession(false);
                if (session != null) {
                    UserSession userSession = (UserSession) session.getAttribute(SessionAuthInterceptor.SESSION_USER);
                    if (userSession != null && userSession.getToken() != null && !userSession.getToken().isBlank()) {
                        return "Bearer " + userSession.getToken();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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
                UserRole role = UserRole.CUSTOMER;
                if (res.role() != null) {
                    if (res.role().contains("ADMIN")) {
                        role = UserRole.ADMIN;
                    } 
                } else if (email.toLowerCase().contains("admin")) { 
                    role = UserRole.ADMIN; 
                }

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
        // Automatically determine role derived from email to allow creating admins easily via web form
        String roleStr = form.getEmail() != null && form.getEmail().toLowerCase().contains("admin") ? "ADMIN" : "CUSTOMER";
        
        RegisterReq req = new RegisterReq(
                form.getFirstName(), form.getLastName(), form.getEmail(),
                form.getContactNo(), form.getBirthDate(), form.getPassword(), roleStr,
                form.getIdType(), form.getIdNumber(), form.getAddress()
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
        CustomerView view = new CustomerView();
        view.setCustomerId(customerId);

        try {
            ApiResponseDto<CustomerRes> response = restClient.get()
                    .uri("/api/customers/{id}", customerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<CustomerRes>>() {});

            if (response != null && response.getData() != null) {
                CustomerRes c = response.getData();
                view.setFirstName(c.firstName());
                view.setLastName(c.lastName());
                view.setEmail(c.email());
                view.setContactNo(c.contactNo());
                view.setBirthDate(c.birthDate());
                view.setIdType(c.idType());
                view.setIdNumber(c.idNumber());
                view.setAddress(c.address());
            }
        } catch (Exception ex) {
            log.warn("Could not fetch customer profile for {}: {}", customerId, ex.getMessage());
        }

        view.setKycStatus(kycStatusOverrides.getOrDefault(customerId, KycStatus.VERIFIED));
        List<AccountView> accounts = getAccountsByCustomerId(customerId);
        view.setAccounts(accounts);
        return Optional.of(view);
    }

    @Override
    public List<CustomerView> getAllCustomers() {
        try {
            ApiResponseDto<List<CustomerRes>> response = restClient.get()
                    .uri("/api/admin/customers")
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<List<CustomerRes>>>() {});

            if (response != null && response.getData() != null) {
                List<AccountView> allAccounts = getAllAccounts();
                java.util.Map<String, List<AccountView>> accountsByCust = allAccounts.stream()
                        .collect(java.util.stream.Collectors.groupingBy(AccountView::getCustomerId));

                return response.getData().stream().map(c -> {
                    CustomerView view = new CustomerView();
                    view.setCustomerId(c.customerId());
                    view.setFirstName(c.firstName());
                    view.setLastName(c.lastName());
                    view.setEmail(c.email());
                    view.setContactNo(c.contactNo());
                    view.setBirthDate(c.birthDate());
                    view.setIdType(c.idType());
                    view.setIdNumber(c.idNumber());
                    view.setAddress(c.address());
                    view.setKycStatus(kycStatusOverrides.getOrDefault(c.customerId(), KycStatus.VERIFIED));
                    view.setAccounts(accountsByCust.getOrDefault(c.customerId(), Collections.emptyList()));
                    return view;
                }).toList();
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    @Override
    public void updateKycStatus(String customerId, KycStatus status) {
        if (customerId != null && status != null) {
            kycStatusOverrides.put(customerId, status);
            log.info("Admin updated customer {} KYC status to {}", customerId, status);
        }
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
        try {
            ApiResponseDto<List<AccountRes>> response = restClient.get()
                    .uri("/api/admin/accounts")
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<List<AccountRes>>>() {});

            if (response != null && response.getData() != null) {
                return response.getData().stream().map(this::mapAccount).toList();
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    @Override
    public AccountView openAccount(String customerId, AccountType type, String currencyCode, BigDecimal initialDeposit) {
        CreateAccountReq req = new CreateAccountReq(
                customerId,
                type != null ? type.name() : "SAVINGS",
                currencyCode != null ? currencyCode : "PHP"
        );

        try {
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
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            String detail = extractErrorDetail(ex.getResponseBodyAsString());
            throw new IllegalArgumentException(detail != null ? detail : "Account creation failed: " + ex.getStatusText());
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            log.error("Accounts service connection error: {}", ex.getMessage());
            throw new IllegalStateException("Accounts service is currently unavailable or timed out. Please try again later.");
        }
    }

    @Override
    public void updateAccountStatus(String accountId, AccountStatus status) {
        try {
            restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/admin/accounts/{accountId}/status")
                            .queryParam("status", status != null ? status.name() : "ACTIVE")
                            .build(accountId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {}
    }

    @Override
    public List<TransactionView> executeTransaction(TransactionForm form) {
        String idempKey = form.getIdempotencyKey();
        if (idempKey == null || idempKey.isBlank()) {
            idempKey = "TXN-KEY-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        MutateReq req = new MutateReq(
                form.getFromAcctNo(),
                form.getToAcctNo(),
                form.getTxnType().name(),
                form.getAmount(),
                idempKey
        );

        try {
            ApiResponseDto<MutateRes> response = restClient.post()
                    .uri("/api/v1/ledger/mutate")
                    .body(req)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<MutateRes>>() {});

            if (response != null && response.getData() != null) {
                MutateRes res = response.getData();
                TransactionView tv = new TransactionView();
                tv.setTxnId(res.txnId());
                tv.setMutationId("MUT-" + (res.txnId().length() >= 8 ? res.txnId().substring(0, 8) : res.txnId()));
                tv.setAccountId(res.accountId());
                tv.setCounterpartyAccountId(form.getToAcctNo());
                tv.setTxnType(form.getTxnType());
                tv.setDirection(form.getTxnType() == TransactionType.DEPOSIT ? MutationDirection.CREDIT : MutationDirection.DEBIT);
                tv.setAmount(res.amount());
                if ("PENDING".equalsIgnoreCase(res.txnStatus())) {
                    tv.setAuditState(AuditState.PENDING);
                } else {
                    tv.setAuditState(AuditState.COMMITTED);
                }
                tv.setTimestamp(LocalDateTime.now());

                // Populate currency & cross-currency attributes on newly initiated txn
                if (form.getFromAcctNo() != null) {
                    getAccountById(form.getFromAcctNo()).ifPresent(a -> tv.setCurrencyCode(a.getCurrencyCode()));
                }
                if (form.getToAcctNo() != null) {
                    getAccountById(form.getToAcctNo()).ifPresent(dest -> {
                        tv.setDestCurrencyCode(dest.getCurrencyCode());
                        if (tv.getCurrencyCode() != null && !tv.getCurrencyCode().equalsIgnoreCase(dest.getCurrencyCode())) {
                            tv.setCrossCurrency(true);
                            BigDecimal rate = getExchangeRate(tv.getCurrencyCode(), dest.getCurrencyCode());
                            tv.setFxRate(rate);
                            if (form.getAmount() != null) {
                                tv.setDestAmount(form.getAmount().multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP));
                            }
                        }
                    });
                }

                return List.of(tv);
            }
            throw new IllegalStateException("Ledger mutation failed: " + (response != null ? response.getMessage() : "Unknown error"));
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            String detail = extractErrorDetail(ex.getResponseBodyAsString());
            throw new IllegalArgumentException(detail != null ? detail : "Transaction failed: " + ex.getStatusText());
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            log.error("Transaction service connection error: {}", ex.getMessage());
            throw new IllegalStateException("Transaction service is currently unavailable or timed out. Please try again later.");
        }
    }

    @Override
    public List<TransactionView> getTransactionsByAccountId(String accountId) {
        try {
            String currency = getAccountById(accountId).map(AccountView::getCurrencyCode).orElse("PHP");

            ApiResponseDto<List<Map<String, Object>>> response = restClient.get()
                    .uri("/api/v1/ledger/audit/account/{accountId}", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<List<Map<String, Object>>>>() {});

            if (response != null && response.getData() != null) {
                return response.getData().stream().map(m -> {
                    TransactionView tv = new TransactionView();
                    String tid = m.get("txnId") != null ? m.get("txnId").toString() : "";
                    tv.setTxnId(tid);
                    tv.setMutationId("MUT-" + (tid.length() >= 8 ? tid.substring(0, 8) : tid));
                    tv.setAccountId(m.get("accountId") != null ? m.get("accountId").toString() : accountId);
                    String cur = m.get("currencyCode") != null ? m.get("currencyCode").toString().trim() : currency;
                    tv.setCurrencyCode(cur);

                    String txnTypeStr = m.get("txnType") != null ? m.get("txnType").toString() : "TRANSFER";
                    try {
                        tv.setTxnType(TransactionType.valueOf(txnTypeStr.toUpperCase()));
                    } catch (Exception e) {
                        tv.setTxnType(TransactionType.TRANSFER);
                    }

                    String mutType = m.get("mutationType") != null ? m.get("mutationType").toString() : "DEBIT";
                    tv.setDirection("CREDIT".equalsIgnoreCase(mutType) ? MutationDirection.CREDIT : MutationDirection.DEBIT);

                    if (m.get("mutationAmount") != null) {
                        tv.setAmount(new BigDecimal(m.get("mutationAmount").toString()));
                    }

                    String auditStateStr = m.get("auditState") != null ? m.get("auditState").toString() : "COMMITTED";
                    try {
                        tv.setAuditState(AuditState.valueOf(auditStateStr.toUpperCase()));
                    } catch (Exception e) {
                        tv.setAuditState(AuditState.COMMITTED);
                    }

                    tv.setTimestamp(parseAuditTimestamp(m.get("createdAt")));
                    return tv;
                }).sorted(Comparator.comparing(TransactionView::getTimestamp).reversed()).toList();
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch audits for account {}: {}", accountId, ex.getMessage());
        }
        return Collections.emptyList();
    }

    private LocalDateTime parseAuditTimestamp(Object value) {
        if (value == null) {
            return LocalDateTime.now();
        }

        String timestamp = value.toString();
        try {
            return Instant.parse(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(timestamp);
            } catch (java.time.format.DateTimeParseException invalidTimestamp) {
                log.warn("Invalid ledger audit timestamp: {}", timestamp);
                return LocalDateTime.now();
            }
        }
    }

    @Override
    public List<TransactionView> getAllTransactions() {
        try {
            List<AccountView> accounts = getAllAccounts();
            List<TransactionView> all = new java.util.ArrayList<>();
            for (AccountView a : accounts) {
                all.addAll(getTransactionsByAccountId(a.getAccountId()));
            }
            all.sort((x, y) -> y.getTimestamp().compareTo(x.getTimestamp()));
            return all;
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    @Override
    public Optional<TransactionView> getTransactionById(String txnId) {
        try {
            ApiResponseDto<List<Map<String, Object>>> response = restClient.get()
                    .uri("/api/v1/ledger/audit/{txnId}", txnId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<List<Map<String, Object>>>>() {});

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                // Short wait and retry once to accommodate distributed ledger replication
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {}
                response = restClient.get()
                        .uri("/api/v1/ledger/audit/{txnId}", txnId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<ApiResponseDto<List<Map<String, Object>>>>() {});
            }

            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                List<Map<String, Object>> audits = response.getData();
                Map<String, Object> first = audits.stream()
                    .filter(audit -> "DEBIT".equalsIgnoreCase(String.valueOf(audit.get("mutationType"))))
                    .findFirst()
                    .orElse(audits.get(0));
                Map<String, Object> second = audits.stream()
                    .filter(audit -> audit != first)
                    .findFirst()
                    .orElse(null);

                TransactionView tv = new TransactionView();
                tv.setTxnId(first.get("txnId") != null ? first.get("txnId").toString() : txnId);
                tv.setMutationId("MUT-" + (tv.getTxnId().length() >= 8 ? tv.getTxnId().substring(0, 8) : tv.getTxnId()));
                tv.setAccountId(first.get("accountId") != null ? first.get("accountId").toString() : "");
                if (first.get("currencyCode") != null) {
                    tv.setCurrencyCode(first.get("currencyCode").toString().trim());
                }

                String txnTypeStr = first.get("txnType") != null ? first.get("txnType").toString() : "TRANSFER";
                try {
                    tv.setTxnType(TransactionType.valueOf(txnTypeStr.toUpperCase()));
                } catch (Exception e) {
                    tv.setTxnType(TransactionType.TRANSFER);
                }

                String mutTypeStr = first.get("mutationType") != null ? first.get("mutationType").toString() : "DEBIT";
                tv.setDirection("CREDIT".equalsIgnoreCase(mutTypeStr) ? MutationDirection.CREDIT : MutationDirection.DEBIT);

                if (first.get("mutationAmount") != null) {
                    tv.setAmount(new BigDecimal(first.get("mutationAmount").toString()));
                }

                if (second != null) {
                    tv.setCounterpartyAccountId(second.get("accountId") != null ? second.get("accountId").toString() : null);
                    if (second.get("currencyCode") != null) {
                        tv.setDestCurrencyCode(second.get("currencyCode").toString().trim());
                    }
                }

                String stateStr = first.get("auditState") != null ? first.get("auditState").toString() : "COMMITTED";
                try {
                    tv.setAuditState(AuditState.valueOf(stateStr.toUpperCase()));
                } catch (Exception e) {
                    tv.setAuditState(AuditState.COMMITTED);
                }

                tv.setTimestamp(parseAuditTimestamp(first.get("createdAt")));

                // Enrich with account currencies and cross-currency metadata
                if (tv.getAccountId() != null && !tv.getAccountId().isBlank()) {
                    getAccountById(tv.getAccountId()).ifPresent(acct -> {
                        if (tv.getCurrencyCode() == null) tv.setCurrencyCode(acct.getCurrencyCode());
                    });
                }
                if (tv.getCounterpartyAccountId() != null && !tv.getCounterpartyAccountId().isBlank()) {
                    getAccountById(tv.getCounterpartyAccountId()).ifPresent(destAcct -> {
                        if (tv.getDestCurrencyCode() == null) tv.setDestCurrencyCode(destAcct.getCurrencyCode());
                        if (tv.getCurrencyCode() != null && !tv.getCurrencyCode().equalsIgnoreCase(destAcct.getCurrencyCode())) {
                            tv.setCrossCurrency(true);
                            if (second != null && second.get("mutationAmount") != null) {
                                BigDecimal destAmt = new BigDecimal(second.get("mutationAmount").toString());
                                tv.setDestAmount(destAmt);
                                if (tv.getAmount() != null && tv.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                                    tv.setFxRate(destAmt.divide(tv.getAmount(), 6, java.math.RoundingMode.HALF_UP));
                                }
                            }
                        }
                    });
                }

                return Optional.of(tv);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch transaction audit for {}: {}", txnId, ex.getMessage());
        }
        return Optional.empty();
    }

    private String extractErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
            String candidate = null;
            if (root.has("message") && !root.get("message").isNull() && !root.get("message").asText().isBlank()) {
                candidate = root.get("message").asText();
            } else if (root.has("detail") && !root.get("detail").isNull() && !root.get("detail").asText().isBlank()) {
                candidate = root.get("detail").asText();
            } else if (root.has("error") && !root.get("error").isNull() && !root.get("error").asText().isBlank()) {
                candidate = root.get("error").asText();
            }

            if (candidate != null) {
                int status = root.has("status") ? root.get("status").asInt() : 500;
                if (status >= 500 || "Internal Server Error".equalsIgnoreCase(candidate)
                        || candidate.contains("I/O error") || candidate.contains("Connection refused")
                        || candidate.contains("http://") || candidate.contains("https://")
                        || candidate.contains("ResourceAccessException")) {
                    return "The accounts service is temporarily unavailable. Please try again shortly.";
                }
                return candidate;
            }
        } catch (Exception ignored) {}

        String trimmed = responseBody.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || trimmed.contains("I/O error") || trimmed.contains("Connection refused")
                || trimmed.contains("http://") || trimmed.contains("https://")) {
            return "The accounts service is temporarily unavailable. Please try again shortly.";
        }
        return responseBody;
    }

    private static final Map<String, BigDecimal> FX_RATES_TO_PHP = Map.of(
            "PHP", BigDecimal.ONE,
            "USD", new BigDecimal("58.50"),
            "EUR", new BigDecimal("63.50"),
            "GBP", new BigDecimal("75.00"),
            "SGD", new BigDecimal("44.50"),
            "JPY", new BigDecimal("0.39")
    );

    @Override
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/fx-rate")
                            .queryParam("sourceCurrency", fromCurrency)
                            .queryParam("targetCurrency", toCurrency)
                            .build())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("exchangeRate")) {
                return new BigDecimal(response.get("exchangeRate").toString());
            }
        } catch (Exception e) {
            log.warn("Forex service unavailable for {} to {}, using cached FX rates: {}", fromCurrency, toCurrency, e.getMessage());
        }

        // Resilient fallback based on FX_RATE_CACHE canonical values
        BigDecimal fromRate = FX_RATES_TO_PHP.getOrDefault(fromCurrency.toUpperCase(), BigDecimal.ONE);
        BigDecimal toRate = FX_RATES_TO_PHP.getOrDefault(toCurrency.toUpperCase(), BigDecimal.ONE);
        return fromRate.divide(toRate, 6, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public List<NotificationView> getNotificationsByCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<AccountView> accounts = getAccountsByCustomerId(customerId);
            List<String> accountIds = accounts.stream().map(AccountView::getAccountId).toList();

            ApiResponseDto<List<NotificationRes>> response = restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/api/notifications/{customerId}");
                        if (!accountIds.isEmpty()) {
                            b.queryParam("accountIds", String.join(",", accountIds));
                        }
                        return b.build(customerId);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDto<List<NotificationRes>>>() {});

            if (response != null && response.getData() != null) {
                return response.getData().stream().map(n -> {
                    LocalDateTime sentAt = n.createdAt() != null
                            ? n.createdAt().toLocalDateTime()
                            : LocalDateTime.now();
                    boolean isRead = "READ".equalsIgnoreCase(n.status());
                    return new NotificationView(
                            n.notifId(),
                            customerId,
                            n.message(),
                            "SMS",
                            n.status() != null ? n.status() : "SENT",
                            sentAt,
                            isRead
                    );
                }).toList();
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch notifications for customer {}: {}", customerId, ex.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public void markNotificationAsRead(String notifId) {
        if (notifId == null || notifId.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/api/notifications/{id}/read", notifId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to mark notification {} as read: {}", notifId, ex.getMessage());
        }
    }

    @Override
    public List<ReconciliationRunView> getReconciliationRuns() {
        try {
            List<ReconRunRes> responses = restClient.get()
                    .uri("/api/recon/runs?includeResults=true")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ReconRunRes>>() {});

            if (responses == null) {
                return Collections.emptyList();
            }

            return responses.stream().map(this::mapReconRun).toList();
        } catch (Exception e) {
            log.error("Failed to fetch reconciliation runs from gateway", e);
            return Collections.emptyList();
        }
    }

    @Override
        public void triggerReconciliationRun(LocalDate startDate, LocalDate endDate) {
        try {
            OffsetDateTime windowStart = startDate.atStartOfDay(BANKING_ZONE).toOffsetDateTime();
            OffsetDateTime windowEnd = endDate.plusDays(1).atStartOfDay(BANKING_ZONE).toOffsetDateTime();
            restClient.post()
                    .uri("/api/recon/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                .body(new ReconRunReq(windowStart, windowEnd))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Triggered reconciliation run via gateway for [{}, {})", windowStart, windowEnd);
        } catch (Exception e) {
            log.error("Failed to trigger reconciliation run via gateway", e);
            throw new RuntimeException("Failed to trigger reconciliation run: " + e.getMessage(), e);
        }
    }

    private ReconciliationRunView mapReconRun(ReconRunRes res) {
        ReconciliationRunView view = new ReconciliationRunView();
        view.setRunId(res.runId() != null ? res.runId().toUpperCase() : "N/A");
        view.setRunStatus(res.status() != null ? res.status() : "UNKNOWN");
        view.setTotalTxnChecked(res.totalChecked());
        view.setTotalMatched(res.totalMatched());
        view.setTotalExceptions(res.totalExceptions());
        view.setWindowStart(res.windowStart() != null ? res.windowStart().toLocalDateTime() : null);
        view.setWindowEnd(res.windowEnd() != null ? res.windowEnd().toLocalDateTime() : null);
        view.setRunStartedAt(res.startedAt() != null ? res.startedAt().toLocalDateTime() : null);
        view.setRunCompletedAt(res.completedAt() != null ? res.completedAt().toLocalDateTime() : null);

        if (res.results() != null && !res.results().isEmpty()) {
            List<ReconciliationResultView> resultViews = res.results().stream()
                    .map(this::mapReconResult)
                    .toList();
            view.setResults(resultViews);
        }
        return view;
    }

    private ReconciliationResultView mapReconResult(ReconResultRes res) {
        ReconciliationResultView view = new ReconciliationResultView();
        view.setResultId(res.resultId());
        view.setRunId(res.runId() != null ? res.runId().toUpperCase() : "N/A");
        view.setTxnId(res.txnId());
        view.setAccountId(res.accountId());
        view.setTransactionDateTime(res.txnCompletedAt() != null ? res.txnCompletedAt().toLocalDateTime()
            : res.ledgerPostedAt() != null ? res.ledgerPostedAt().toLocalDateTime()
            : res.createdAt() != null ? res.createdAt().toLocalDateTime() : null);
        view.setReconStatus(res.reconStatus());
        view.setExceptionType(res.exceptionType());
        view.setExpectedCurrencyCode(res.expectedCurrencyCode());
        view.setActualCurrencyCode(res.actualCurrencyCode());
        view.setExpectedAmount(res.expectedAmount());
        view.setActualAmount(res.actualAmount());
        view.setVarianceAmount(res.varianceAmount() != null ? res.varianceAmount() : BigDecimal.ZERO);
        view.setPostingLagSeconds(res.postingLagSeconds() != null ? res.postingLagSeconds() : 0);
        view.setSeverity(res.severity() != null ? res.severity() : "LOW");
        view.setCreatedAt(res.createdAt() != null ? res.createdAt().toLocalDateTime() : null);
        return view;
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

