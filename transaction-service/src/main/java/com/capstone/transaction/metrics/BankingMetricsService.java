package com.capstone.transaction.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Central home for all banking business metrics.
 *
 * Metrics exposed:
 *
 *   banking_transactions_total{type, status}
 *     Incremented exactly once per transaction at its final business outcome.
 *     type   : DEPOSIT | WITHDRAWAL | TRANSFER
 *     status : SUCCESS | FAILED
 *
 *   banking_idempotency_conflicts_total
 *     Incremented when an in-flight duplicate is detected via Redis SET NX
 *     (tryLock returns false).  Completed-result replays (getCached hit) are
 *     NOT counted here because they are successful idempotent replays, not
 *     conflicts.
 *
 * IMPORTANT cardinality rules (enforced here):
 *   - transactionId, accountId, customerId are NEVER used as labels.
 *   - Only low-cardinality labels (type, status) are permitted.
 *
 * MDC context is set alongside every metric update so the same event can be
 * correlated in Loki logs without carrying high-cardinality data in Prometheus.
 */
@Service
public class BankingMetricsService {

    private static final String METRIC_TRANSACTIONS      = "banking_transactions_total";
    private static final String METRIC_IDEMPOTENCY       = "banking_idempotency_conflicts_total";

    private static final String TAG_TYPE   = "type";
    private static final String TAG_STATUS = "status";

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED  = "FAILED";

    // ── Pre-built counters (one per low-cardinality label combination) ────────
    // Pre-building avoids lookup overhead on the hot path and ensures all
    // counters are registered at startup so Prometheus shows them at zero
    // even before the first transaction.

    private final Counter depositSuccess;
    private final Counter depositFailed;
    private final Counter withdrawalSuccess;
    private final Counter withdrawalFailed;
    private final Counter transferSuccess;
    private final Counter transferFailed;
    private final Counter idempotencyConflicts;

    public BankingMetricsService(MeterRegistry registry) {
        this.depositSuccess = buildCounter(registry, "DEPOSIT",    STATUS_SUCCESS);
        this.depositFailed  = buildCounter(registry, "DEPOSIT",    STATUS_FAILED);

        this.withdrawalSuccess = buildCounter(registry, "WITHDRAWAL", STATUS_SUCCESS);
        this.withdrawalFailed  = buildCounter(registry, "WITHDRAWAL", STATUS_FAILED);

        this.transferSuccess = buildCounter(registry, "TRANSFER",   STATUS_SUCCESS);
        this.transferFailed  = buildCounter(registry, "TRANSFER",   STATUS_FAILED);

        this.idempotencyConflicts = Counter.builder(METRIC_IDEMPOTENCY)
                .description("Number of in-flight duplicate transactions blocked by the idempotency guard")
                .register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call AFTER idempotencyService.storeResult() — i.e. the transaction has
     * been fully committed in Oracle + PostgreSQL and the result has been
     * durably stored in Redis.
     *
     * @param txnType one of: DEPOSIT, WITHDRAWAL, TRANSFER
     * @param txnId   used only for MDC / log correlation — NOT a metric label
     */
    public void recordSuccess(String txnType, String txnId) {
        counter(txnType, STATUS_SUCCESS).increment();
        setMdc(txnId, txnType, STATUS_SUCCESS, null);
    }

    /**
     * Call AFTER idempotencyService.release() — i.e. the exception path after
     * a genuine business or infrastructure failure.
     *
     * @param txnType one of: DEPOSIT, WITHDRAWAL, TRANSFER
     * @param txnId   used only for MDC / log correlation — NOT a metric label
     * @param reason  short failure category (e.g. INSUFFICIENT_FUNDS, TIMEOUT)
     *                — used only for MDC / log correlation — NOT a metric label
     */
    public void recordFailure(String txnType, String txnId, String reason) {
        counter(txnType, STATUS_FAILED).increment();
        setMdc(txnId, txnType, STATUS_FAILED, reason);
    }

    /**
     * Call when IdempotencyService.tryLock() returns false — an in-flight
     * duplicate was detected and rejected before any balance mutation occurred.
     */
    public void recordIdempotencyConflict() {
        idempotencyConflicts.increment();
    }

    /**
     * Clear MDC keys set by this service.  Call from a finally block after
     * the log statement that uses the MDC context has been written.
     */
    public void clearMdc() {
        MDC.remove("transactionId");
        MDC.remove("txnType");
        MDC.remove("txnStatus");
        MDC.remove("reason");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Counter counter(String txnType, String status) {
        return switch (txnType) {
            case "DEPOSIT"    -> STATUS_SUCCESS.equals(status) ? depositSuccess    : depositFailed;
            case "WITHDRAWAL" -> STATUS_SUCCESS.equals(status) ? withdrawalSuccess : withdrawalFailed;
            case "TRANSFER"   -> STATUS_SUCCESS.equals(status) ? transferSuccess   : transferFailed;
            default -> throw new IllegalArgumentException(
                    "Unknown txnType for metric: " + txnType);
        };
    }

    private static Counter buildCounter(MeterRegistry registry,
                                        String txnType,
                                        String status) {
        return Counter.builder(METRIC_TRANSACTIONS)
                .description("Total banking transactions by type and outcome")
                .tag(TAG_TYPE,   txnType)
                .tag(TAG_STATUS, status)
                .register(registry);
    }

    private static void setMdc(String txnId, String txnType,
                                String status, String reason) {
        if (txnId   != null) MDC.put("transactionId", txnId);
        if (txnType != null) MDC.put("txnType",       txnType);
        if (status  != null) MDC.put("txnStatus",     status);
        if (reason  != null) MDC.put("reason",        reason);
        else                 MDC.remove("reason");
    }
}
