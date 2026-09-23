
package com.capstone.transaction.service;

import com.capstone.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Evicts balance:{accountId} from Redis after a successful Oracle balance
 * mutation so accounts-service cache-aside reads fall through to Oracle and
 * pick up the updated balance_amount.
 * accountId is a String UUID matching CUSTOMER_BALANCE_MASTER.account_id.
 */
@Component
@RequiredArgsConstructor
public class BalanceCacheInvalidator {

    private final StringRedisTemplate redisTemplate;

    public void evict(String accountId) {
        redisTemplate.delete(RedisKeys.balanceKey(accountId));
    }
}

