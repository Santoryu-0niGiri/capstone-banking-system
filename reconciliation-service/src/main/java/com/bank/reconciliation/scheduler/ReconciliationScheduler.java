package com.bank.reconciliation.scheduler;

import com.bank.reconciliation.service.ReconciliationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * "On a regular schedule, it quietly compares..." — fires every
 * recon.schedule-cron (default: every 15 minutes) and checks today's
 * activity from midnight in the banking timezone through the run time.
 */
@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);
    private static final ZoneId BANKING_ZONE = ZoneId.of("Asia/Manila");

    private final ReconciliationEngine reconciliationEngine;

    public ReconciliationScheduler(ReconciliationEngine reconciliationEngine) {
        this.reconciliationEngine = reconciliationEngine;
    }

    @Scheduled(cron = "${recon.schedule-cron}")
    public void runScheduledReconciliation() {
        OffsetDateTime windowEnd = OffsetDateTime.now(BANKING_ZONE);
        OffsetDateTime windowStart = ZonedDateTime.now(BANKING_ZONE)
                .toLocalDate()
                .atStartOfDay(BANKING_ZONE)
                .toOffsetDateTime();
        log.info("Scheduled reconciliation firing for window [{}, {})", windowStart, windowEnd);
        reconciliationEngine.run(windowStart, windowEnd);
    }
}
