package com.capstone.gateway.filter;

import com.capstone.gateway.security.ReactiveJwtValidator;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Gateway edge-authentication filter. Applied to protected routes in
 * application.yml. Validates the bearer token's signature/expiry, checks
 * the Redis blacklist (token:{jwt}) populated by login-service on logout,
 * and forwards the resolved customer id downstream via the X-Cust-Id
 * header so resource services don't need to re-parse the token if they
 * don't want to.
 */
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private final ReactiveJwtValidator jwtValidator;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationGatewayFilterFactory(ReactiveJwtValidator jwtValidator,
                                                  ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.jwtValidator = jwtValidator;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String header = request.getHeaders().getFirst("Authorization");

            if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
                return unauthorized(exchange, "Missing or malformed Authorization header");
            }
            String token = header.substring(7);

            return redisTemplate.hasKey("token:" + token)
                    .defaultIfEmpty(false)
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return unauthorized(exchange, "Token has been revoked");
                        }
                        Optional<Claims> claims = jwtValidator.validate(token);
                        if (claims.isEmpty()) {
                            return unauthorized(exchange, "Invalid or expired token");
                        }
                        return forward(exchange, chain, claims.get());
                    });
        };
    }

    private Mono<Void> forward(org.springframework.web.server.ServerWebExchange exchange,
                                org.springframework.cloud.gateway.filter.GatewayFilterChain chain,
                                Claims claims) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Cust-Id", claims.getSubject())
                .header("X-Cust-Email", String.valueOf(claims.get("email")))
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(org.springframework.web.server.ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", reason);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // no per-route configuration needed today; reserved for future use
    }
}
