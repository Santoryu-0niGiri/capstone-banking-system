package com.capstone.common.constants;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static final String BALANCE_PREFIX = "balance:";
    public static final String IDEMPOTENCY_PREFIX = "idempotency:";
    public static final String TOKEN_PREFIX = "token:";

    public static String balanceKey(Long acctNo) {
        return BALANCE_PREFIX + acctNo;
    }

    public static String idempotencyKey(String key) {
        return IDEMPOTENCY_PREFIX + key;
    }

    public static String tokenKey(String jwt) {
        return TOKEN_PREFIX + jwt;
    }
}
