
package com.capstone.gateway.filter;

import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.capstone.gateway.security.ReactiveJwtValidator;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

/**
 * Gateway edge-authentication filter. Applied to protected routes in
 * application.yml.
 *
 * Responsibilities:
 * - Validates the Bearer JWT.
 * - Checks the Redis blacklist for revoked tokens.
 * - Forwards the authenticated customer information downstream.
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
        String customerEmail = String.valueOf(claims.get("email"));

        /*
         * IMPORTANT:
         *
         * Do not directly modify:
         *
         * exchange.getRequest().getHeaders()
         *
         * Those headers can be read-only.
         *
         * Instead, create a mutable copy of the existing headers and
         * replace/add the headers on the copied request.
         */

        HttpHeaders headers = new HttpHeaders();

        headers.putAll(exchange.getRequest().getHeaders());

        headers.set("X-Cust-Id", customerId);
        headers.set("X-Cust-Email", customerEmail);

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(existingHeaders -> {
                    existingHeaders.clear();
                    existingHeaders.putAll(headers);
                })
                .build();

        org.springframework.web.server.ServerWebExchange mutatedExchange =
                exchange.mutate()
                        .request(mutatedRequest)
                        .build();

        return chain.filter(mutatedExchange);
    }

    private Mono<Void> unauthorized(
            org.springframework.web.server.ServerWebExchange exchange,
            String reason) {

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

        exchange.getResponse()
                .getHeaders()
                .add("X-Auth-Error", reason);

        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // No per-route configuration needed today.
        // Reserved for future configuration.
    }
}
