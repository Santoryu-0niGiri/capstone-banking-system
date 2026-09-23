package com.capstone.transaction.service;

import com.capstone.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * After any successful balance mutation, evicts the shared balance:{acctNo}
 * Redis cache entry (populated by accounts-service) so subsequent reads
 * are forced back to Oracle and pick up the new balance instead of serving
 * a stale cached value.
 */
@Component
@RequiredArgsConstructor
public class BalanceCacheInvalidator {

    private final StringRedisTemplate redisTemplate;

    public void evict(Long acctNo) {
        redisTemplate.delete(RedisKeys.balanceKey(acctNo));
    }
}
