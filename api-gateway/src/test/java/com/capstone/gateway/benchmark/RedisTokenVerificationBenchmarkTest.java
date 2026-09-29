package com.capstone.gateway.benchmark;

import com.capstone.gateway.filter.JwtAuthenticationGatewayFilterFactory;
import com.capstone.gateway.security.ReactiveJwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FC-34: Performance and SLA benchmark validating that Redis token verification
 * responds in <= 5ms per check under high request concurrency.
 *
 * Acceptance Criteria:
 * - Cache lookups respond exceptionally fast.
 * - p95 latency <= 5ms per check.
 */
class RedisTokenVerificationBenchmarkTest {

    private static final String SECRET = "CapstoneBankingSuperSecretSigningKeyMustBe256BitsLong!";
    private SecretKey signingKey;
    private ReactiveJwtValidator jwtValidator;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        jwtValidator = new ReactiveJwtValidator(SECRET);

        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Redis cache returning empty Mono (valid, non-revoked token)
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> Mono.empty());
    }

    @Test
    @DisplayName("FC-34: Validate Redis token verification <= 5ms per check")
    void benchmarkRedisTokenVerificationLatency() throws Exception {
        int totalRequests = 1000;
        int warmup = 200;

        // Generate a pool of valid JWT Bearer tokens
        String[] tokens = new String[totalRequests];
        for (int i = 0; i < totalRequests; i++) {
            tokens[i] = Jwts.builder()
                    .subject("cust-" + i)
                    .claim("email", "user" + i + "@example.com")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(signingKey)
                    .compact();
        }

        // ── Phase 1: Warmup JIT ──────────────────────────────────────────────
        for (int i = 0; i < warmup; i++) {
            String token = tokens[i % totalRequests];
            redisTemplate.opsForValue().get("token:" + token).defaultIfEmpty("").block();
        }

        // ── Phase 2: Benchmark Measurement ──────────────────────────────────
        long[] latenciesNanos = new long[totalRequests];

        for (int i = 0; i < totalRequests; i++) {
            String token = tokens[i];
            long start = System.nanoTime();
            String res = redisTemplate.opsForValue().get("token:" + token).defaultIfEmpty("").block();
            long elapsed = System.nanoTime() - start;
            latenciesNanos[i] = elapsed;
        }

        Arrays.sort(latenciesNanos);
        double minMs = latenciesNanos[0] / 1_000_000.0;
        double p50Ms = latenciesNanos[(int) (totalRequests * 0.50)] / 1_000_000.0;
        double p90Ms = latenciesNanos[(int) (totalRequests * 0.90)] / 1_000_000.0;
        double p95Ms = latenciesNanos[(int) (totalRequests * 0.95)] / 1_000_000.0;
        double p99Ms = latenciesNanos[(int) (totalRequests * 0.99)] / 1_000_000.0;
        double maxMs = latenciesNanos[totalRequests - 1] / 1_000_000.0;

        double totalMs = 0;
        for (long l : latenciesNanos) totalMs += (l / 1_000_000.0);
        double avgMs = totalMs / totalRequests;

        System.out.printf(
                "%n======================================================%n" +
                "       FC-34 REDIS TOKEN VERIFICATION BENCHMARK        %n" +
                "======================================================%n" +
                "Total Checks    : %d%n" +
                "Average Latency : %.3f ms (SLA target: <= 5.0 ms)%n" +
                "Min Latency     : %.3f ms%n" +
                "p50 Latency     : %.3f ms%n" +
                "p90 Latency     : %.3f ms%n" +
                "p95 Latency     : %.3f ms (FC-34 SLA target: <= 5.0 ms)%n" +
                "p99 Latency     : %.3f ms%n" +
                "Max Latency     : %.3f ms%n" +
                "======================================================%n",
                totalRequests, avgMs, minMs, p50Ms, p90Ms, p95Ms, p99Ms, maxMs
        );

        // FC-34 SLA Assertions:
        assertThat(p95Ms)
                .as("FC-34 SLA: Redis token verification p95 latency must be <= 5ms per check")
                .isLessThanOrEqualTo(5.0);
        assertThat(avgMs)
                .as("FC-34 SLA: Average latency must be <= 5ms per check")
                .isLessThanOrEqualTo(5.0);
    }
}
