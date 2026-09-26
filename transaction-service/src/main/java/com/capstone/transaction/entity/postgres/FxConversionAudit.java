package com.capstone.transaction.entity.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fx_conversion_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxConversionAudit {

    @Id
    @GeneratedValue
    @Column(name = "conversion_id", updatable = false, nullable = false)
    private UUID conversionId;

    @Column(name = "txn_id", nullable = false, length = 36)
    private String txnId;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "dest_currency", nullable = false, length = 3)
    private String destCurrency;

    @Column(name = "source_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "dest_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal destAmount;

    @Column(name = "conversion_status", nullable = false, length = 20)
    private String conversionStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

}

