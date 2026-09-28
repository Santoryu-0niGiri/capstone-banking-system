package com.bank.reconciliation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "recon")
@Getter
@Setter
public class ReconProperties {

    private int windowMinutes = 60;
    private String scheduleCron = "0 */15 * * * *";
    private Topics topics = new Topics();
    private Matching matching = new Matching();
    private OutboxRelay outboxRelay = new OutboxRelay();

    @Getter
    @Setter
    public static class Topics {
        private String discrepancy = "reconciliation.discrepancy";
        private String runCompleted = "reconciliation.run.completed";
    }

    @Getter
    @Setter
    public static class Matching {
        private BigDecimal amountTolerance = new BigDecimal("0.01");
        private int latePostingThresholdSeconds = 300;
    }

    @Getter
    @Setter
    public static class OutboxRelay {
        private long pollFixedDelayMs = 2000;
        private int batchSize = 100;
    }
}
