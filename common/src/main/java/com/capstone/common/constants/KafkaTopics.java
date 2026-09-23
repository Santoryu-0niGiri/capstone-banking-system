
package com.capstone.common.constants;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String TRANSACTION_EVENTS    = "transaction-events";
    public static final String CUSTOMER_REGISTERED   = "customer.registered";
    public static final String ACCOUNT_CREATED       = "account.created";
    public static final String BALANCE_UPDATED       = "balance.updated";
    public static final String RECON_DISCREPANCY     = "reconciliation.discrepancy";
}

