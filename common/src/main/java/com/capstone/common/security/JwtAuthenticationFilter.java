
package com.capstone.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless JWT bearer-token filter used by every WebMVC resource service.
 * Principal is now a String UUID (customer_id from APP_USER_MASTER).
 * Rejects tokens blacklisted in Redis via token:{jwt} key set by login-service on logout.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, StringRedisTemplate redisTemplate) {
        this.tokenProvider = tokenProvider;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && tokenProvider.isValid(token) && !isBlacklisted(token)) {
            // Principal is String UUID — downstream services cast SecurityContext.principal to String
            String customerId = tokenProvider.getCustomerId(token);
            List<String> roles = tokenProvider.getRoles(token);
            List<SimpleGrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().map(SimpleGrantedAuthority::new).toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(customerId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isBlacklisted(String token) {
        if (redisTemplate == null) return false;
        try {
            String value = redisTemplate.opsForValue().get("token:" + token);
            return "BLACKLISTED".equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}

