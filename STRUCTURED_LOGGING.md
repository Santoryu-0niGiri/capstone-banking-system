# Structured Logging & Infrastructure Observability

## Overview

This document describes the complete structured logging, Loki log ingestion, and Grafana observability pipeline for monitoring Reconciliation, Database, and Redis infrastructure across the Capstone Banking System microservices.

## Key Changes Summary

### 1. Logback Configuration (All Services)
- JSON encoder via `logstash-logback-encoder:7.4`
- Profile-based switching: plain text (local) vs. JSON (docker)
- MDC fields: `component`, `event_type`, `transactionId`, `reason`, `service`
- Async appender prevents back-pressure blocking

### 2. Instrumentation Points

#### Reconciliation Engine
- Logs all mismatch detections with MDC `component=reconciliation`, `event_type=LEDGER_MISMATCH`
- Exception types: AMOUNT_MISMATCH, CURRENCY_MISMATCH, STATUS_MISMATCH, ACCOUNT_MISMATCH, MISSING_LEDGER_ENTRY, ORPHAN_LEDGER_ENTRY

#### Global Exception Handler (common module)
- Catches HikariCP pool exhaustion: `component=database`, `event_type=DB_CONNECTION_TIMEOUT`
- Catches deadlocks: `component=database`, `event_type=DB_DEADLOCK`
- Catches Redis timeouts: `component=redis`, `event_type=REDIS_CONNECTION_TIMEOUT`

### 3. Promtail Configuration
- Extracts JSON fields from LogstashEncoder output
- Promotes labels: `service`, `level`, `component`, `event_type`
- Total label cardinality: ~840 streams (manageable for Loki)
- Drops noise: /actuator/health, /actuator/prometheus

### 4. Grafana Dashboard: "System Infrastructure & Reconciliation Logs"
File: `grafana/provisioning/dashboards/infrastructure-logs.json`

**Panels** (11 total):
1. Reconciliation Execution Logs (live stream)
2. Redis Connection & Timeout Logs (live stream)
3. Database Connection & Pool Exhaustion Logs (live stream)
4. Redis & DB Connection Error Rate over Time (time series)
5. Reconciliation Mismatch Count by Type (time series)
6. Connection Pool Exhaustion Events (time series)
7. Log Volume by Component (bar chart)
8. Filtered Logs by Transaction ID (live stream)
9. Deadlock Detection Events (time series)
10. Redis Timeout Events (time series)
11. Ledger Settlement Events (time series)

## Files Modified

- `common/pom.xml` — Added logstash-logback-encoder dependency
- `common/src/main/java/com/capstone/common/exception/GlobalExceptionHandler.java` — Added DB/Redis exception handlers with MDC logging
- `reconciliation-service/pom.xml` — Added logstash-logback-encoder dependency
- `reconciliation-service/src/main/java/com/bank/reconciliation/service/ReconciliationEngine.java` — Added MDC instrumentation for mismatch logging
- `reconciliation-service/src/main/resources/logback-spring.xml` — Created standard JSON logging config
- `transaction-service/src/main/resources/logback-spring.xml` — Updated with component, event_type MDC fields
- `forex-service/src/main/resources/logback-spring.xml` — Updated with component, event_type MDC fields
- `accounts-service/src/main/resources/logback-spring.xml` — Created standard JSON logging config
- `accounts-service/pom.xml` — Added logstash-logback-encoder dependency
- `login-service/src/main/resources/logback-spring.xml` — Created standard JSON logging config
- `registration-service/src/main/resources/logback-spring.xml` — Created standard JSON logging config
- `api-gateway/src/main/resources/logback-spring.xml` — Created standard JSON logging config
- `promtail/promtail-config.yml` — Updated pipeline to extract component, event_type labels
- `grafana/provisioning/dashboards/infrastructure-logs.json` — Created comprehensive dashboard

## Test Results

All unit tests passed:
- `reconciliation-service`: 11/11 tests ✓
- `transaction-service`: 16/16 tests ✓
- `forex-service`: 7/7 tests ✓

## Deployment Steps

1. Rebuild services: `docker-compose down && docker-compose up -d --build`
2. Verify log ingestion: Query `{component="reconciliation"}` in Grafana Explore
3. Trigger test scenarios to populate dashboards
4. Monitor in real-time via "System Infrastructure & Reconciliation Logs" dashboard

## MDC Context Fields

| Field | Type | Cardinality | Source |
|-------|------|-------------|--------|
| `service` | String | Low | `spring.application.name` |
| `component` | String | Low | reconciliation \| database \| redis |
| `event_type` | String | Low | LEDGER_MISMATCH \| DB_CONNECTION_TIMEOUT \| REDIS_CONNECTION_TIMEOUT |
| `level` | String | Low | ERROR \| WARN \| INFO \| DEBUG |
| `transactionId` | UUID | High | Application logic (logs only, not metrics) |
| `reason` | String | High | Exception message (logs only, not metrics) |

## LogQL Query Examples

**All reconciliation mismatches (last hour):**
```logql
{component="reconciliation", event_type="LEDGER_MISMATCH"}
```

**Database connection errors by service (rate/min):**
```logql
sum(rate({component="database", level="ERROR"} [1m])) by (service)
```

**Redis timeouts aggregated:**
```logql
sum(count_over_time({component="redis", event_type="REDIS_CONNECTION_TIMEOUT"} [5m])) by (service)
```

**Transaction-specific debugging:**
```logql
{} | json | transactionId = "550e8400-e29b-41d4-a716-446655440000"
```
