package com.capstone.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_balance_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerBalanceMaster {

    @Id
    @Column(
        name = "account_id",
        length = 36,
        nullable = false,
        updatable = false
    )
    private String accountId;

    @Column(
        name = "customer_id",
        length = 36,
        nullable = false
    )
    private String customerId;

    @Column(
        name = "account_type",
        length = 30,
        nullable = false
    )
    private String accountType;

    /*
     * Oracle database column:
     * currency_code CHAR(3) NOT NULL
     *
     * Explicitly map this field as CHAR(3) so Hibernate's
     * schema validation matches the existing Oracle schema.
     */
    @Column(
        name = "currency_code",
        columnDefinition = "CHAR(3)",
        nullable = false
    )
    private String currencyCode;

    @Column(
        name = "account_status",
        length = 20,
        nullable = false
    )
    private String accountStatus;

    @Column(
        name = "balance_amount",
        precision = 18,
        scale = 4,
        nullable = false
    )
    private BigDecimal balanceAmount;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
        name = "created_by",
        length = 50,
        nullable = false
    )
    private String createdBy;

    @Column(
        name = "updated_at"
    )
    private LocalDateTime updatedAt;

    @Column(
        name = "updated_by",
        length = 50
    )
    private String updatedBy;
}