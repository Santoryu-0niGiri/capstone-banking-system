package com.capstone.transaction.concurrency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance Criteria:
 * Script executes parallel withdrawals against single accounts to test
 * concurrency locks.
 *
 * Implemented in pure Java:
 * 1. Contains a main() method to run directly as a standalone Java script.
 * 2. Contains a JUnit 5 @Test method to run within Maven or the IDE test runner.
 */
public class ParallelWithdrawalConcurrencyTest {

    private static final String BASE_URL = System.getProperty("baseUrl",
            System.getenv().getOrDefault("BASE_URL", "http://localhost:8080"));
    private static final String DEFAULT_EMAIL = System.getProperty("email",
            System.getenv().getOrDefault("TEST_EMAIL", "concurrency.test@example.com"));
    private static final String DEFAULT_PASSWORD = System.getProperty("password",
            System.getenv().getOrDefault("TEST_PASSWORD", "Password123"));

    private static final String ORACLE_URL = System.getProperty("oracle.url",
            System.getenv().getOrDefault("ORACLE_URL", "jdbc:oracle:thin:@localhost:1521/XEPDB1"));
    private static final String ORACLE_USER = System.getProperty("oracle.user",
            System.getenv().getOrDefault("ORACLE_USERNAME", "ledger_app"));
    private static final String ORACLE_PASSWORD = System.getProperty("oracle.password",
            System.getenv().getOrDefault("ORACLE_PASSWORD", "LedgerAppPass123"));

    private static final int THREAD_COUNT = 10;
    private static final BigDecimal WITHDRAW_AMOUNT = new BigDecimal("100.00");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Standalone Java entry point to run as a script.
     * Usage:
     *   java ... ParallelWithdrawalConcurrencyTest [accountId] [token]
     */
    public static void main(String[] args) throws Exception {
        System.out.println("======================================================================");
        System.out.println(" CONCURRENCY LOCK TEST: PARALLEL WITHDRAWALS (JAVA)");
        System.out.println("======================================================================");

        String customAccountId = args.length > 0 ? args[0] : null;
        String customToken = args.length > 1 ? args[1] : null;

        ParallelWithdrawalConcurrencyTest runner = new ParallelWithdrawalConcurrencyTest();
        runner.executeParallelWithdrawalTest(customAccountId, customToken);
    }

    @Test
    @DisplayName("Executes parallel withdrawals against single account to verify concurrency locks")
    void testParallelWithdrawals() throws Exception {
        // Skip if local server is not running during automated Maven build
        Assumptions.assumeTrue(isServerAlive(), "Gateway/Transaction service is not running on " + BASE_URL);
        executeParallelWithdrawalTest(null, null);
    }

    public void executeParallelWithdrawalTest(String specificAccountId, String specificToken) throws Exception {
        // 1. Authenticate (login or auto-register test user)
        String token = (specificToken != null && !specificToken.isBlank())
                ? specificToken
                : authenticateOrRegister();
        System.out.println("[+] Acquired JWT Bearer token.");

        // 2. Obtain an account with funded balance
        String accountId = (specificAccountId != null && !specificAccountId.isBlank())
                ? specificAccountId
                : obtainFundedAccount(token);
        System.out.println("[+] Testing Account ID: " + accountId);

        // 3. Query initial balance
        BigDecimal initialBalance = getAccountBalance(accountId, token);
        System.out.printf(Locale.US, "[+] Initial Balance: $%.2f%n", initialBalance);
        System.out.printf(Locale.US, "[+] Firing %d simultaneous withdrawal requests ($%.2f each)...%n",
                THREAD_COUNT, WITHDRAW_AMOUNT);

        // 4. Fire parallel withdrawals simultaneously using CountDownLatch
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 1; i <= THREAD_COUNT; i++) {
            final int workerId = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready, then strike simultaneously
                    startGate.await();

                    String idempotencyKey = UUID.randomUUID().toString();
                    String requestBody = String.format(Locale.US, """
                            {
                                "accountId": "%s",
                                "counterpartyAccountId": null,
                                "txnType": "WITHDRAWAL",
                                "amount": %.2f,
                                "idempotencyKey": "%s"
                            }
                            """, accountId, WITHDRAW_AMOUNT, idempotencyKey);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/v1/ledger/mutate"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .timeout(Duration.ofSeconds(15))
                            .build();

                    long start = System.currentTimeMillis();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    long latency = System.currentTimeMillis() - start;

                    int status = response.statusCode();
                    if (status == 201) {
                        successCount.incrementAndGet();
                        System.out.printf("  Worker %2d -> Status 201 CREATED (%d ms) [SUCCESS]%n", workerId, latency);
                    } else if (status == 422) {
                        insufficientCount.incrementAndGet();
                        System.out.printf("  Worker %2d -> Status 422 UNPROCESSABLE (%d ms) [INSUFFICIENT FUNDS]%n",
                                workerId, latency);
                    } else {
                        errorCount.incrementAndGet();
                        System.out.printf("  Worker %2d -> Status %d (%d ms) [ERROR]: %s%n", workerId, status, latency,
                                response.body());
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.printf("  Worker %2d -> Exception: %s%n", workerId, e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Release all threads simultaneously
        long testStartTime = System.currentTimeMillis();
        startGate.countDown();
        boolean completedInTime = doneGate.await(30, TimeUnit.SECONDS);
        long totalDuration = System.currentTimeMillis() - testStartTime;
        executor.shutdown();

        assertThat(completedInTime).as("All parallel withdrawals should complete within 30 seconds").isTrue();

        // 5. Query final balance
        BigDecimal finalBalance = getAccountBalance(accountId, token);

        System.out.println("----------------------------------------------------------------------");
        System.out.printf(Locale.US, "Completed %d parallel requests in %d ms%n", THREAD_COUNT, totalDuration);
        System.out.printf(Locale.US, "Successful Withdrawals (201) : %d%n", successCount.get());
        System.out.printf(Locale.US, "Rejected / Insufficient (422): %d%n", insufficientCount.get());
        System.out.printf(Locale.US, "Errors                       : %d%n", errorCount.get());
        System.out.println("======================================================================");
        System.out.println(" VERIFICATION & INVARIANT CHECK");
        System.out.println("======================================================================");
        System.out.printf(Locale.US, "Initial Balance     : $%.2f%n", initialBalance);
        BigDecimal totalDeducted = WITHDRAW_AMOUNT.multiply(new BigDecimal(successCount.get()));
        System.out.printf(Locale.US, "Total Deducted      : $%.2f%n", totalDeducted);
        BigDecimal expectedBalance = initialBalance.subtract(totalDeducted);
        System.out.printf(Locale.US, "Expected Balance    : $%.2f%n", expectedBalance);
        System.out.printf(Locale.US, "Actual Final Balance: $%.2f%n", finalBalance);

        // 6. Assert Concurrency Lock Invariants
        assertThat(errorCount.get()).as("No unexpected 500 errors should occur under concurrency").isEqualTo(0);
        assertThat(finalBalance).as("Actual balance must equal expected balance (no lost updates)")
                .isEqualByComparingTo(expectedBalance);
        assertThat(finalBalance).as("Balance must never become negative under concurrent load")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);

        System.out.println("\n[PASSED] CONCURRENCY LOCK TEST PASSED!");
        System.out.println(" -> Pessimistic row locking successfully serialized parallel mutations.");
        System.out.println(" -> Zero lost updates. No negative balances occurred.");
    }

    private boolean isServerAlive() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/login"))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String authenticateOrRegister() throws Exception {
        String loginPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", DEFAULT_EMAIL, DEFAULT_PASSWORD);
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> loginResp = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
        if (loginResp.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(loginResp.body());
            return root.path("data").path("token").asText();
        }

        // If not registered yet, auto-register
        String regPayload = String.format("""
                {
                    "firstName": "Concurrency",
                    "lastName": "Tester",
                    "email": "%s",
                    "contactNo": "+1234567890",
                    "birthDate": "1990-01-01",
                    "password": "%s"
                }
                """, DEFAULT_EMAIL, DEFAULT_PASSWORD);

        HttpRequest regReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(regPayload))
                .timeout(Duration.ofSeconds(5))
                .build();

        httpClient.send(regReq, HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> postRegLogin = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(postRegLogin.body());
        return root.path("data").path("token").asText();
    }

    private String obtainFundedAccount(String token) throws Exception {
        // 1. Check if provided by system property or environment variable
        String envAcct = System.getProperty("accountId", System.getenv("ACCOUNT_ID"));
        if (envAcct != null && !envAcct.isBlank()) {
            return envAcct.trim();
        }

        // 2. Try REST API if accounts-service is reachable
        try {
            String customerId = getCustomerIdFromTokenOrLogin(token);
            if (customerId != null && !customerId.isBlank()) {
                HttpRequest acctListReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/accounts/customer/" + customerId))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .timeout(Duration.ofSeconds(3))
                        .build();

                HttpResponse<String> acctListResp = httpClient.send(acctListReq, HttpResponse.BodyHandlers.ofString());
                if (acctListResp.statusCode() == 200) {
                    JsonNode list = objectMapper.readTree(acctListResp.body()).path("data");
                    if (list.isArray() && list.size() > 0) {
                        String existingId = list.get(0).path("accountId").asText();
                        if (existingId != null && !existingId.isBlank()) {
                            BigDecimal currentBal = getAccountBalance(existingId, token);
                            if (currentBal.compareTo(new BigDecimal("200")) >= 0) {
                                return existingId;
                            }
                            deposit(existingId, new BigDecimal("1000.00"), token);
                            return existingId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[!] Notice: REST account lookup fallback: " + e.getMessage());
        }

        // 3. Fallback to direct JDBC seed/query
        return seedOrFetchAccountViaJdbc();
    }

    private String getCustomerIdFromTokenOrLogin(String token) {
        try {
            HttpRequest userReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            String.format("{\"email\":\"%s\",\"password\":\"%s\"}", DEFAULT_EMAIL, DEFAULT_PASSWORD)))
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = httpClient.send(userReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode userJson = objectMapper.readTree(resp.body());
                return userJson.path("data").path("customerId").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String seedOrFetchAccountViaJdbc() throws Exception {
        try (Connection conn = DriverManager.getConnection(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD)) {
            conn.setAutoCommit(true);

            java.util.List<String> tables = new java.util.ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_name FROM user_tables WHERE table_name IN ('ACCOUNT_MASTER', 'CUSTOMER_BALANCE_MASTER')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tables.add(rs.getString(1).toUpperCase());
                    }
                }
            }
            System.out.println("[+] Existing Oracle account tables: " + tables);

            // Ensure customer exists
            String customerId = UUID.randomUUID().toString();
            try (PreparedStatement checkCust = conn.prepareStatement("SELECT customer_id FROM customer_master WHERE email = ?")) {
                checkCust.setString(1, DEFAULT_EMAIL);
                try (ResultSet rs = checkCust.executeQuery()) {
                    if (rs.next()) {
                        customerId = rs.getString("customer_id");
                    } else {
                        try (PreparedStatement insCust = conn.prepareStatement(
                                "INSERT INTO customer_master (customer_id, first_name, last_name, email, contact_no, created_at, created_by) " +
                                        "VALUES (?, 'Concurrency', 'Tester', ?, '+1234567890', SYSTIMESTAMP, 'SYSTEM')")) {
                            insCust.setString(1, customerId);
                            insCust.setString(2, DEFAULT_EMAIL);
                            insCust.executeUpdate();
                        }
                    }
                }
            }

            // Create a new account and seed it into ALL existing tables
            String newAccountId = UUID.randomUUID().toString();
            for (String tName : tables) {
                String insertSql = "INSERT INTO " + tName + " " +
                        "(account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_at, created_by, version) " +
                        "VALUES (?, ?, 'SAVINGS', 'USD', 'ACTIVE', 1000.00, SYSTIMESTAMP, 'SYSTEM', 0)";
                try (PreparedStatement insAcct = conn.prepareStatement(insertSql)) {
                    insAcct.setString(1, newAccountId);
                    insAcct.setString(2, customerId);
                    insAcct.executeUpdate();
                    System.out.println("[+] Seeded account " + newAccountId + " into " + tName);
                } catch (Exception e) {
                    System.err.println("[!] Insert into " + tName + " failed: " + e.getMessage());
                }
            }

            return newAccountId;
        }
    }

    private void deposit(String accountId, BigDecimal amount, String token) throws Exception {
        String depositPayload = String.format(Locale.US, """
                {
                    "accountId": "%s",
                    "counterpartyAccountId": null,
                    "txnType": "DEPOSIT",
                    "amount": %.2f,
                    "idempotencyKey": "%s"
                }
                """, accountId, amount, UUID.randomUUID());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v1/ledger/mutate"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(depositPayload))
                .build();
        httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private BigDecimal getAccountBalance(String accountId, String token) {
        // Try REST API endpoint first
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/accounts/" + accountId + "/balance"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode data = root.path("data");
                if (!data.isMissingNode() && !data.isNull() && !data.asText().isBlank()) {
                    return new BigDecimal(data.asText());
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback to direct JDBC query
        try (Connection conn = DriverManager.getConnection(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD)) {
            java.util.List<String> tables = new java.util.ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_name FROM user_tables WHERE table_name IN ('ACCOUNT_MASTER', 'CUSTOMER_BALANCE_MASTER')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tables.add(rs.getString(1).toUpperCase());
                    }
                }
            }
            for (String tName : tables) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT balance_amount FROM " + tName + " WHERE account_id = ?")) {
                    ps.setString(1, accountId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getBigDecimal("balance_amount");
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch balance for account " + accountId, e);
        }

        throw new IllegalStateException("Account " + accountId + " not found in database");
    }
}
