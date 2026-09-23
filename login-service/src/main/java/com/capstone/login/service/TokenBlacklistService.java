package com.capstone.login.service;

import com.capstone.common.constants.RedisKeys;
import com.capstone.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    /**
     * Registers an issued token so login-service and every downstream
     * resource service can recognize it as currently active. Mirrors the
     * key convention token:{jwt} demanded by the spec.
     */
    public void registerActiveToken(String token, long expirySeconds) {
        redisTemplate.opsForValue().set(RedisKeys.tokenKey(token), "ACTIVE", Duration.ofSeconds(expirySeconds));
    }

    /**
     * On logout, flips the token's Redis value to BLACKLISTED and keeps it
     * alive only until the token's own natural expiry, so the key never
     * outlives the JWT itself.
     */
    public void blacklist(String token) {
        Claims claims = tokenProvider.parseClaims(token);
        long remainingSeconds = Math.max(1, (claims.getExpiration().getTime() - Instant.now().toEpochMilli()) / 1000);
        redisTemplate.opsForValue().set(RedisKeys.tokenKey(token), "BLACKLISTED", Duration.ofSeconds(remainingSeconds));
    }
}
