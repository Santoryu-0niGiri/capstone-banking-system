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
 * Redis-backed idempotency guard for transaction mutation endpoints. Keys:
 * idempotency:{key} with a 24h TTL as required by the spec. A value of
 * "PROCESSING" marks an in-flight request (guards against concurrent
 * duplicate submission); once the transaction completes the value is
 * replaced with the serialized TransactionResponse so retried requests
 * receive the original result instead of re-executing the mutation.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String PROCESSING_MARKER = "PROCESSING";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<TransactionResponse> getCached(String idempotencyKey) {
        String json = redisTemplate.opsForValue().get(RedisKeys.idempotencyKey(idempotencyKey));
        if (json == null || PROCESSING_MARKER.equals(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, TransactionResponse.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

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

    public void release(String idempotencyKey) {
        redisTemplate.delete(RedisKeys.idempotencyKey(idempotencyKey));
    }
}
