package com.capstone.gateway.filter;

import com.capstone.gateway.security.ReactiveJwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationGatewayFilterFactoryTest {

    private static final String SECRET = "CapstoneBankingSuperSecretSigningKeyMustBe256BitsLong!";
    private SecretKey signingKey;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private GatewayFilterChain chain;

    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        ReactiveJwtValidator validator = new ReactiveJwtValidator(SECRET);
        JwtAuthenticationGatewayFilterFactory factory =
                new JwtAuthenticationGatewayFilterFactory(validator, redisTemplate);
        filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());
    }

    @Test
    @DisplayName("Valid token passes filter and forwards to downstream service")
    void validToken_passesFilter() {
        String token = Jwts.builder()
                .subject("cust-uuid-12345")
                .claim("email", "john.doe@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey)
                .compact();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token:" + token)).thenReturn(Mono.empty());
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Downstream status is not set to 401
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Expired token returns HTTP 401 Unauthorized")
    void expiredToken_returns401() {
        String expiredToken = Jwts.builder()
                .subject("cust-uuid-12345")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(signingKey)
                .compact();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token:" + expiredToken)).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("Invalid or expired token");
    }

    @Test
    @DisplayName("Forged token returns HTTP 401 Unauthorized")
    void forgedToken_returns401() {
        SecretKey attackerKey = Keys.hmacShaKeyFor("AttackerForgedKeyMustBeAtLeast256BitsLongForHmacShaSecret!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .subject("attacker")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(attackerKey)
                .compact();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token:" + forgedToken)).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("Invalid or expired token");
    }
}
