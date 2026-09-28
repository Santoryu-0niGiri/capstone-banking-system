package com.bank.reconciliation.entity.oracle;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-only mapping onto the Oracle account_master table owned by
 * accounts-service (see the real DDL: account_id, customer_id,
 * account_type, currency_code, account_status, balance_amount,
 * NUMBER(18,4), plus audit columns and an optimistic-lock version).
 * Recon never writes here.
 */
@Entity
@Table(name = "ACCOUNT_MASTER")
@Getter
@Setter
public class AccountMaster {

    @Id
    @Column(name = "ACCOUNT_ID", length = 36)
    private String accountId;

    @Column(name = "CUSTOMER_ID", length = 36, nullable = false)
    private String customerId;

    @Column(name = "ACCOUNT_TYPE", length = 30, nullable = false)
    private String accountType; // SAVINGS / CHECKING / WALLET

    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "ACCOUNT_STATUS", length = 20, nullable = false)
    private String accountStatus; // ACTIVE / FROZEN / CLOSED

    @Column(name = "BALANCE_AMOUNT", precision = 18, scale = 4, nullable = false)
    private BigDecimal balanceAmount;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
