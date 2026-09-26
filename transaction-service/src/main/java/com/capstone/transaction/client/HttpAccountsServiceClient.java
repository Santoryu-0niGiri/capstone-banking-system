package com.capstone.transaction.client;

import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.AccountMutationRequest;
import com.capstone.common.dto.AccountMutationResponse;
import com.capstone.common.dto.ApiResponse;
import com.capstone.common.exception.IdempotencyConflictException;
import com.capstone.common.exception.InsufficientBalanceException;
import com.capstone.common.exception.ResourceNotFoundException;
import com.capstone.common.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * High-performance REST client for inter-service communication with Accounts Service.
 * Configured with persistent connection pooling, keep-alive, custom timeouts,
 * and automatic Bearer JWT propagation.
 */
@Component
@Slf4j
public class HttpAccountsServiceClient implements AccountsServiceClient {

    private final RestClient restClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public HttpAccountsServiceClient(
            @Value("${accounts.service.url:http://localhost:8083}") String accountsServiceUrl,
            JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper) {

        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;

        // Configure Java 21 pooled HTTP client with HTTP/1.1 persistent connections
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(accountsServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    byte[] bodyBytes = response.getBody().readAllBytes();
                    String responseBody = new String(bodyBytes, StandardCharsets.UTF_8);
                    String detailMessage = extractDetail(responseBody);

                    int statusCode = response.getStatusCode().value();
                    if (statusCode == 422) {
                        throw new InsufficientBalanceException(
                                detailMessage != null ? detailMessage : "Insufficient balance");
                    } else if (statusCode == 404) {
                        throw new ResourceNotFoundException(
                                detailMessage != null ? detailMessage : "Account not found");
                    } else if (statusCode == 409) {
                        throw new IdempotencyConflictException(
                                detailMessage != null ? detailMessage : "Conflict in account mutation");
                    } else {
                        throw new IllegalStateException(
                                "Accounts Service error [" + statusCode + "]: " + detailMessage);
                    }
                })
                .build();
    }

    @Override
    public AccountMutationResponse debit(String accountId, BigDecimal amount, String txnId, String txnType) {
        String authHeader = resolveBearerToken();
        AccountMutationRequest request = new AccountMutationRequest(amount, txnId, txnType);

        ApiResponse<AccountMutationResponse> response = restClient.post()
                .uri("/api/accounts/{accountId}/debit", accountId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<AccountMutationResponse>>() {});

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Empty response from Accounts Service debit for account " + accountId);
        }

        return response.data();
    }

    @Override
    public AccountMutationResponse credit(String accountId, BigDecimal amount, String txnId, String txnType) {
        String authHeader = resolveBearerToken();
        AccountMutationRequest request = new AccountMutationRequest(amount, txnId, txnType);

        ApiResponse<AccountMutationResponse> response = restClient.post()
                .uri("/api/accounts/{accountId}/credit", accountId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<AccountMutationResponse>>() {});

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Empty response from Accounts Service credit for account " + accountId);
        }

        return response.data();
    }

    @Override
    public AccountDTO getAccount(String accountId) {
        String authHeader = resolveBearerToken();

        ApiResponse<AccountDTO> response = restClient.get()
                .uri("/api/accounts/{accountId}", accountId)
                .header("Authorization", authHeader)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<AccountDTO>>() {});

        if (response == null || response.data() == null) {
            throw new ResourceNotFoundException("Account " + accountId + " not found");
        }

        return response.data();
    }

    private String resolveBearerToken() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest req = attributes.getRequest();
                String authHeader = req.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader;
                }
            }
        } catch (Exception ignored) {
        }

        // Generate an internal system service token if not in an active user HTTP request context
        String token = jwtTokenProvider.generateToken(
                "SYSTEM_TXN_SERVICE",
                "system-txn@internal.bank",
                List.of("ROLE_ADMIN", "ROLE_INTERNAL")
        );
        return "Bearer " + token;
    }

    private String extractDetail(String json) {
        if (json == null || json.isBlank()) return "Unknown error";
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("detail") && !node.get("detail").isNull()) {
                return node.get("detail").asText();
            }
            if (node.has("message") && !node.get("message").isNull()) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return json;
    }
}

