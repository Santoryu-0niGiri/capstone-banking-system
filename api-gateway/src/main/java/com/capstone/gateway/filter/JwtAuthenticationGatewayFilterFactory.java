package com.capstone.gateway.filter;

import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.capstone.gateway.security.ReactiveJwtValidator;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

/**
 * Gateway edge-authentication filter.
 * Applied to protected routes in application.yml.
 *
 * Responsibilities:
 * - Validates the Bearer JWT.
 * - Checks the Redis blacklist for revoked tokens.
 * - Forwards authenticated customer information downstream.
 */
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private final ReactiveJwtValidator jwtValidator;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationGatewayFilterFactory(
            ReactiveJwtValidator jwtValidator,
            ReactiveStringRedisTemplate redisTemplate) {

        super(Config.class);
        this.jwtValidator = jwtValidator;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {

        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();

            String authorizationHeader =
                    request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (!StringUtils.hasText(authorizationHeader)
                    || !authorizationHeader.startsWith("Bearer ")) {

                return unauthorized(
                        exchange,
                        "Missing or malformed Authorization header"
                );
            }

            String token = authorizationHeader.substring(7);

            return redisTemplate.opsForValue()
                    .get("token:" + token)
                    .defaultIfEmpty("")
                    .onErrorReturn("")
                    .flatMap(value -> {

                        if ("BLACKLISTED".equals(value)) {
                            return unauthorized(
                                    exchange,
                                    "Token has been revoked"
                            );
                        }

                        Optional<Claims> claims =
                                jwtValidator.validate(token);

                        if (claims.isEmpty()) {
                            return unauthorized(
                                    exchange,
                                    "Invalid or expired token"
                            );
                        }

                        return forward(
                                exchange,
                                chain,
                                claims.get()
                        );
                    });
        };
    }

    private Mono<Void> forward(
            org.springframework.web.server.ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain,
            Claims claims) {

        String customerId = claims.getSubject();

        Object emailClaim = claims.get("email");
        String customerEmail =
                emailClaim != null ? emailClaim.toString() : "";

        /*
         * Create a decorated request instead of modifying the original
         * read-only request headers.
         */
        ServerHttpRequest decoratedRequest =
                new ServerHttpRequestDecorator(exchange.getRequest()) {

                    @Override
                    @NonNull
                    public HttpHeaders getHeaders() {

                        HttpHeaders headers = new HttpHeaders();

                        // Copy the original request headers.
                        headers.putAll(super.getHeaders());

                        // Add authenticated customer information.
                        headers.set("X-Cust-Id", customerId);
                        headers.set("X-Cust-Email", customerEmail);

                        return headers;
                    }
                };

        return chain.filter(
                exchange.mutate()
                        .request(decoratedRequest)
                        .build()
        );
    }

    private Mono<Void> unauthorized(
            org.springframework.web.server.ServerWebExchange exchange,
            String reason) {

        exchange.getResponse()
                .setStatusCode(HttpStatus.UNAUTHORIZED);

        exchange.getResponse()
                .getHeaders()
                .add("X-Auth-Error", reason);

        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // Reserved for future configuration.
    }
}
