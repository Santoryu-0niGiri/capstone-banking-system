package com.capstone.transaction.client;

import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.AccountMutationResponse;

import java.math.BigDecimal;

/**
 * Client interface for synchronous balance mutations and lookups
 * against Accounts Service.
 */
public interface AccountsServiceClient {

    /**
     * Atomically debits the specified account in Accounts Service.
     */
    AccountMutationResponse debit(String accountId, BigDecimal amount, String txnId, String txnType);

    /**
     * Atomically credits the specified account in Accounts Service.
     */
    AccountMutationResponse credit(String accountId, BigDecimal amount, String txnId, String txnType);

    /**
     * Retrieves account metadata (balance, status, currency).
     */
    AccountDTO getAccount(String accountId);
}

