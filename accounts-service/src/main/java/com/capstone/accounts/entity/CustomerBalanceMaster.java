
package com.capstone.accounts.entity;

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
 * Maps to CUSTOMER_BALANCE_MASTER (Oracle XE 21c).
 *
 * account_id : VARCHAR2(36) UUID — client-assigned, no IDENTITY.
 * balance_amount : NUMBER(18,4), never negative (DDL CHECK balance_amount >= 0).
 * account_type   : CHECK SAVINGS|CHECKING|WALLET.
 * account_status : CHECK ACTIVE|FROZEN|CLOSED.
 * currency_code  : CHAR(3), e.g. PHP, USD.
 *
 * @Version optimistic lock is kept for accounts-service read paths (account
 * CRUD). Transaction-service acquires PESSIMISTIC_WRITE on its own mapping
 * of this same table to serialize concurrent balance mutations.
 */
@Entity
@Table(name = "customer_balance_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerBalanceMaster {

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

    // NUMBER(18,4) — matches @Digits(integer=14, fraction=4) at the API layer
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

