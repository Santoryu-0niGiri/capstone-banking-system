package com.capstone.accounts.service;

import com.capstone.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside wrapper around Redis for the balance:{acctNo} key. Read paths
 * consult the cache first and fall through to Oracle on a miss; write paths
 * (invoked by accounts-service after any update, and by transaction-service
 * after a successful mutation) refresh or evict the entry so cached reads
 * never serve stale balances for long.
 */
@Service
@RequiredArgsConstructor
public class BalanceCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public Optional<BigDecimal> get(Long acctNo) {
        String value = redisTemplate.opsForValue().get(RedisKeys.balanceKey(acctNo));
        return Optional.ofNullable(value).map(BigDecimal::new);
    }

    public void put(Long acctNo, BigDecimal balance) {
        redisTemplate.opsForValue().set(RedisKeys.balanceKey(acctNo), balance.toPlainString(), TTL);
    }

    public void evict(Long acctNo) {
        redisTemplate.delete(RedisKeys.balanceKey(acctNo));
    }
}
