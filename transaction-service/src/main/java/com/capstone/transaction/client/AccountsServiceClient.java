package com.capstone.transaction.client;

import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.AccountMutationResponse;

import java.math.BigDecimal;

public interface AccountsServiceClient {

    AccountMutationResponse debit(String accountId, BigDecimal amount, String txnId, String txnType);

    AccountMutationResponse credit(String accountId, BigDecimal amount, String txnId, String txnType);

    AccountDTO getAccount(String accountId);
}
