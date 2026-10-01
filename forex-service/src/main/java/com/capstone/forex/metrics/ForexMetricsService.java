package com.capstone.forex.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * ForEx-specific metrics service for conversion tracking.
 *
 * Metrics exposed:
 *
 *   forex_conversions_total{currency_pair, status}
 *     Incremented once per conversion at its final outcome.
 *     status : SUCCESS | FAILED
 *     currency_pair : PHP_USD, PHP_EUR, etc.
 *
 * IMPORTANT: currency_pair is a low-cardinality label (limited to ~10-20 pairs)
 * and is safe for use. High-cardinality fields like txnId, accountId are NOT used as labels.
 */
@Service
public class ForexMetricsService {

    private static final String METRIC_CONVERSIONS = "forex_conversions_total";
    private static final String TAG_PAIR   = "currency_pair";
    private static final String TAG_STATUS = "status";

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED  = "FAILED";

    // Pre-built counters for common pairs + statuses
    private final Counter phpUsdSuccess;
    private final Counter phpUsdFailed;
    private final Counter phpEurSuccess;
    private final Counter phpEurFailed;
    private final Counter phpGbpSuccess;
    private final Counter phpGbpFailed;
    private final Counter phpJpySuccess;
    private final Counter phpJpyFailed;

    // Generic fallback for any pair
    private final MeterRegistry registry;

    public ForexMetricsService(MeterRegistry registry) {
        this.registry = registry;

        // Pre-build common currency pairs
        this.phpUsdSuccess = buildCounter(registry, "PHP_USD", STATUS_SUCCESS);
        this.phpUsdFailed  = buildCounter(registry, "PHP_USD", STATUS_FAILED);

        this.phpEurSuccess = buildCounter(registry, "PHP_EUR", STATUS_SUCCESS);
        this.phpEurFailed  = buildCounter(registry, "PHP_EUR", STATUS_FAILED);

        this.phpGbpSuccess = buildCounter(registry, "PHP_GBP", STATUS_SUCCESS);
        this.phpGbpFailed  = buildCounter(registry, "PHP_GBP", STATUS_FAILED);

        this.phpJpySuccess = buildCounter(registry, "PHP_JPY", STATUS_SUCCESS);
        this.phpJpyFailed  = buildCounter(registry, "PHP_JPY", STATUS_FAILED);
    }

    /**
     * Record a successful ForEx conversion.
     *
     * @param txnId         used only for MDC / log correlation — NOT a metric label
     * @param sourceCurrency e.g. "PHP"
     * @param destCurrency   e.g. "USD"
     */
    public void recordSuccess(String txnId, String sourceCurrency, String destCurrency) {
        String pair = buildPairKey(sourceCurrency, destCurrency);
        counter(pair, STATUS_SUCCESS).increment();
        setMdc(txnId, pair, STATUS_SUCCESS, null);
    }

    /**
     * Record a failed ForEx conversion.
     *
     * @param txnId         used only for MDC / log correlation — NOT a metric label
     * @param sourceCurrency e.g. "PHP"
     * @param destCurrency   e.g. "USD"
     * @param reason        failure reason (e.g., "RATE_FETCH_FAILED") — MDC only
     */
    public void recordFailure(String txnId, String sourceCurrency, String destCurrency, String reason) {
        String pair = buildPairKey(sourceCurrency, destCurrency);
        counter(pair, STATUS_FAILED).increment();
        setMdc(txnId, pair, STATUS_FAILED, reason);
    }

    /**
     * Clear MDC keys set by this service.
     */
    public void clearMdc() {
        MDC.remove("transactionId");
        MDC.remove("currencyPair");
        MDC.remove("conversionStatus");
        MDC.remove("reason");
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private Counter counter(String pair, String status) {
        return switch (pair) {
            case "PHP_USD" -> STATUS_SUCCESS.equals(status) ? phpUsdSuccess    : phpUsdFailed;
            case "PHP_EUR" -> STATUS_SUCCESS.equals(status) ? phpEurSuccess    : phpEurFailed;
            case "PHP_GBP" -> STATUS_SUCCESS.equals(status) ? phpGbpSuccess    : phpGbpFailed;
            case "PHP_JPY" -> STATUS_SUCCESS.equals(status) ? phpJpySuccess    : phpJpyFailed;
            default -> Counter.builder(METRIC_CONVERSIONS)
                    .description("ForEx conversions by currency pair and outcome")
                    .tag(TAG_PAIR,   pair)
                    .tag(TAG_STATUS, status)
                    .register(registry);
        };
    }

    private static Counter buildCounter(MeterRegistry registry, String pair, String status) {
        return Counter.builder(METRIC_CONVERSIONS)
                .description("ForEx conversions by currency pair and outcome")
                .tag(TAG_PAIR,   pair)
                .tag(TAG_STATUS, status)
                .register(registry);
    }

    private static String buildPairKey(String source, String dest) {
        if (source == null || dest == null) {
            return "UNKNOWN";
        }
        return source.trim().toUpperCase() + "_" + dest.trim().toUpperCase();
    }

    private static void setMdc(String txnId, String pair, String status, String reason) {
        if (txnId  != null) MDC.put("transactionId",   txnId);
        if (pair   != null) MDC.put("currencyPair",    pair);
        if (status != null) MDC.put("conversionStatus", status);
        if (reason != null) MDC.put("reason",          reason);
        else                MDC.remove("reason");
    }
}
