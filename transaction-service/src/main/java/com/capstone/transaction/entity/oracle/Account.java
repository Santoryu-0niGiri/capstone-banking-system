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
 * transaction-service's own mapping of the shared Oracle `account` table.
 * It owns balance-mutation writes (debit/credit/transfer) under
 * PESSIMISTIC_WRITE locking; accounts-service owns account CRUD and cached
 * reads. Both point at the same physical table by design (ledger pattern).
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @Column(name = "acct_no")
    private Long acctNo;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "acct_type", nullable = false, length = 20)
    private String acctType;

    @Column(name = "acct_status", nullable = false, length = 20)
    private String acctStatus;

    @Column(name = "balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
