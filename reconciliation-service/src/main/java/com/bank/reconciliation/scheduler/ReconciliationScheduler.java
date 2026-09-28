package com.bank.reconciliation.scheduler;

import com.bank.reconciliation.config.ReconProperties;
import com.bank.reconciliation.service.ReconciliationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * "On a regular schedule, it quietly compares..." — fires every
 * recon.schedule-cron (default: every 15 minutes) and checks the
 * trailing recon.window-minutes of activity. For an end-of-day full
 * sweep, call POST /api/recon/runs with an explicit window instead.
 */
@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationEngine reconciliationEngine;
    private final ReconProperties props;

    public ReconciliationScheduler(ReconciliationEngine reconciliationEngine, ReconProperties props) {
        this.reconciliationEngine = reconciliationEngine;
        this.props = props;
    }

    @Scheduled(cron = "${recon.schedule-cron}")
    public void runScheduledReconciliation() {
        OffsetDateTime windowEnd = OffsetDateTime.now();
        OffsetDateTime windowStart = windowEnd.minusMinutes(props.getWindowMinutes());
        log.info("Scheduled reconciliation firing for window [{}, {})", windowStart, windowEnd);
        reconciliationEngine.run(windowStart, windowEnd);
    }
}
