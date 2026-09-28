package com.capstone.forex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for forex-service.
 *
 * This service is an internal backend service (Kafka consumer + FX rate provider).
 * All user-facing authentication and authorisation is enforced at the API Gateway
 * before requests ever reach this service.
 *
 * The only requirement here is to permit /actuator/** so that Prometheus can
 * scrape metrics without receiving HTTP 401. All other requests are also
 * permitted because the gateway already validated the JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/fx-rate/**").permitAll()
                        .requestMatchers("/api/v1/forex/**").permitAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}
