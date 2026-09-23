package com.capstone.accounts.service;

import com.capstone.accounts.entity.Account;
import com.capstone.accounts.repository.AccountRepository;
import com.capstone.common.dto.AccountDTO;
import com.capstone.common.dto.CreateAccountRequest;
import com.capstone.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final BalanceCacheService balanceCacheService;

    @Transactional
    public AccountDTO createAccount(CreateAccountRequest request) {
        Account account = Account.builder()
                .custId(request.custId())
                .acctType(request.acctType())
                .acctStatus("ACTIVE")
                .balance(BigDecimal.ZERO)
                .build();
        Account saved = accountRepository.save(account);
        balanceCacheService.put(saved.getAcctNo(), saved.getBalance());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public AccountDTO getAccount(Long acctNo) {
        Account account = accountRepository.findById(acctNo)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + acctNo + " not found"));
        return toDto(account);
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> getAccountsForCustomer(Long custId) {
        return accountRepository.findByCustId(custId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long acctNo) {
        return balanceCacheService.get(acctNo)
                .orElseGet(() -> {
                    Account account = accountRepository.findById(acctNo)
                            .orElseThrow(() -> new ResourceNotFoundException("Account " + acctNo + " not found"));
                    balanceCacheService.put(acctNo, account.getBalance());
                    return account.getBalance();
                });
    }

    private AccountDTO toDto(Account account) {
        return new AccountDTO(account.getAcctNo(), account.getCustId(), account.getAcctType(),
                account.getAcctStatus(), account.getBalance(), account.getCreatedAt(), account.getVersion());
    }
}
