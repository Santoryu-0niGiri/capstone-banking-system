
package com.capstone.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Gateway-level Spring Security config. Authentication/authorization itself
 * is enforced by {@link com.capstone.gateway.filter.JwtAuthenticationGatewayFilterFactory}
 * on protected routes declared in application.yml; this chain simply
 * disables CSRF/basic-auth defaults and permits all requests through to
 * the gateway filters, which is the standard pattern for an edge gateway
 * that delegates real authn/z to route-scoped filters.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}

