package com.capstone.transaction.entity.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_mutation_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerMutationAudit {

    @Id
    @Column(name = "txn_id")
    private UUID txnId;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @Column(name = "mutation_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal mutationAmount;

    @Column(name = "txn_type", nullable = false, length = 20)
    private String txnType;

    @Column(name = "counterparty_acct_id")
    private Long counterpartyAcctId;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "audit_state", nullable = false, length = 20)
    private String auditState;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
}
