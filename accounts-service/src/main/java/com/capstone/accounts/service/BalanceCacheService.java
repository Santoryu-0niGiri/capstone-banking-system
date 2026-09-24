
package com.capstone.accounts.service;

import com.capstone.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside wrapper for balance:{accountId} in Redis.
 * accountId is a String UUID matching CUSTOMER_ACCOUNT.account_id.
 * TTL is 5 minutes; transaction-service evicts the entry on every successful
 * mutation so reads never serve stale balances beyond the TTL window.
 */
@Service
@RequiredArgsConstructor
public class BalanceCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public Optional<BigDecimal> get(String accountId) {
        String value = redisTemplate.opsForValue().get(RedisKeys.balanceKey(accountId));
        return Optional.ofNullable(value).map(BigDecimal::new);
    }

    public void put(String accountId, BigDecimal balance) {
        redisTemplate.opsForValue().set(RedisKeys.balanceKey(accountId), balance.toPlainString(), TTL);
    }

    public void evict(String accountId) {
        redisTemplate.delete(RedisKeys.balanceKey(accountId));
    }
}

