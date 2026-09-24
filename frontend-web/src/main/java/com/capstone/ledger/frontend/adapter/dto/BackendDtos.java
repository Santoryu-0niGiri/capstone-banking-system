package com.capstone.ledger.frontend.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BackendDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegisterReq(
            String firstName,
            String lastName,
            String email,
            String contactNo,
            LocalDate birthDate,
            String password
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegisterRes(
            String customerId,
            String firstName,
            String lastName,
            String email
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginReq(
            String email,
            String password
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRes(
            String token,
            String tokenType,
            long expiresInSeconds,
            String customerId,
            String email
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateAccountReq(
            String customerId,
            String accountType,
            String currencyCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountRes(
            String accountId,
            String customerId,
            String accountType,
            String accountStatus,
            BigDecimal balanceAmount,
            String currencyCode,
            LocalDateTime createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MutateReq(
            String accountId,
            String counterpartyAccountId,
            String txnType,
            BigDecimal amount,
            String idempotencyKey
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MutateRes(
            String txnId,
            String accountId,
            String txnType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String txnStatus,
            String timestamp
    ) {}
}

