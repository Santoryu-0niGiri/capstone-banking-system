
package com.capstone.transaction.service;

import com.capstone.common.constants.RedisKeys;
import com.capstone.common.dto.TransactionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed idempotency guard.  Keys: idempotency:{key}, TTL 24 h.
 *
 * "PROCESSING" is a sentinel written atomically via SET … NX to gate
 * concurrent duplicate submissions; on completion it is replaced with the
 * serialised TransactionResponse so retries receive the original result.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration TTL               = Duration.ofHours(24);
    private static final String   PROCESSING_MARKER = "PROCESSING";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    public Optional<TransactionResponse> getCached(String idempotencyKey) {
        String json = redisTemplate.opsForValue().get(RedisKeys.idempotencyKey(idempotencyKey));
        if (json == null || PROCESSING_MARKER.equals(json)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, TransactionResponse.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** @return true if the lock was acquired (first caller), false if already in-flight */
    public boolean tryLock(String idempotencyKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeys.idempotencyKey(idempotencyKey), PROCESSING_MARKER, TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public void storeResult(String idempotencyKey, TransactionResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(RedisKeys.idempotencyKey(idempotencyKey), json, TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent transaction result", e);
        }
    }

    /** Called on any exception path so the key does not permanently block retries */
    public void release(String idempotencyKey) {
        redisTemplate.delete(RedisKeys.idempotencyKey(idempotencyKey));
    }
}

