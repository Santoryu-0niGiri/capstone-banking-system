package com.capstone.transaction.entity.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * transaction-service's write-side mapping of ACCOUNT_MASTER.
 *
 * Owns balance-mutation writes (debit/credit/transfer) under
 * PESSIMISTIC_WRITE locking via AccountRepository#findByIdForUpdate.
 * accounts-service owns the CRUD / cached-read path against the same table.
 *
 * account_status CHECK : ACTIVE | FROZEN | CLOSED
 * balance_amount       : NUMBER(18,4), never goes negative (DDL CHECK >= 0)
 */
@Entity
@Table(name = "account_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountMaster {

    @Id
    @Column(name = "account_id", length = 36, updatable = false, nullable = false)
    private String accountId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus;

    // NUMBER(18,4) — mirrors @Digits(integer=14, fraction=4) at the API layer
    @Column(name = "balance_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

