package com.capstone.common.constants;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static final String BALANCE_PREFIX =
            "balance:";

    public static final String IDEMPOTENCY_PREFIX =
            "idempotency:";

    public static final String TOKEN_PREFIX =
            "token:";

    public static final String TRANSACTION_ID_PREFIX =
            "transaction-id:";

    /** accountId is a String UUID from CUSTOMER_BALANCE_MASTER.account_id */
    public static String balanceKey(String accountId) {
        return BALANCE_PREFIX + accountId;
    }

    public static String idempotencyKey(String key) {
        return IDEMPOTENCY_PREFIX + key;
    }

    public static String tokenKey(String jwt) {
        return TOKEN_PREFIX + jwt;
    }

    /**
     * Redis key for the transaction UUID generated/captured
     * by TransactionIdInterceptor.
     */
    public static String transactionIdKey(String transactionId) {
        return TRANSACTION_ID_PREFIX + transactionId;
    }
}