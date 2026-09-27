# reconciliation-service

The "internal auditor" from the platform architecture: on a schedule (default
every 15 minutes, trailing a 60-minute window), it compares what
`transaction_master` (Oracle) says should have happened against what
`ledger_mutation_audit` (Postgres) actually recorded, flags anything that
doesn't line up into `recon_result_audit`, and publishes each discrepancy to
Kafka via the transactional outbox pattern (`outbox_audit`).

Built against the real platform schema (Oracle `LEDGER_APP` + Postgres audit
DB) — table/column names match exactly.

**This service owns no schema and ships no migrations.** Every table it
touches (`account_master`, `transaction_master` on Oracle; `recon_run_audit`,
`recon_result_audit`, `ledger_mutation_audit`, `outbox_audit` on Postgres)
already exists — you create them yourself by running `01_oracle_main_db.sql`
and `01_postgres_audit_db.sql` against your Oracle XE and Postgres instances
before starting this app. Hibernate is configured with `ddl-auto: validate`
on both datasources, so at startup it only checks its entity mappings
against your existing columns — it never issues a `CREATE`, `ALTER`, or
`DROP`. There is no Flyway (or any other migration tool) in this project.

## What's here

```
reconciliation-service/
├── pom.xml
├── Dockerfile
├── docker-compose.snippet.yml        # paste into your existing compose file
├── src/main/resources/
│   └── application.yml               # dual datasource + recon.* settings
└── src/main/java/com/bank/reconciliation/
    ├── config/        # OracleConfig, PostgresConfig, Kafka, ReconProperties
    ├── entity/oracle/  # AccountMaster, TransactionMaster   (read-only)
    ├── entity/postgres/# ReconRunAudit, ReconResultAudit, LedgerMutationAudit, OutboxAudit
    ├── repository/     # split oracle/ vs postgres/, matching the two EntityManagers
    ├── dto/
    │   ├── ExpectedLeg.java         # expands one transaction into its 1-2 expected ledger legs
    │   └── LegMatchCandidate.java   # one expected leg + whatever actual rows matched it
    ├── service/
    │   ├── MatchingService.java        # pulls + pairs both sides of a window, leg by leg
    │   ├── ExceptionClassifier.java    # pure rules -> MATCHED or one of your 7 exception types
    │   ├── ReconciliationEngine.java   # orchestrates one full run, transactional
    │   ├── OutboxWriter.java           # writes outbox_audit rows in the same tx
    │   └── OutboxRelay.java            # polls outbox_audit, publishes to Kafka
    ├── scheduler/ReconciliationScheduler.java
    ├── controller/ReconciliationController.java   # POST/GET /api/recon/...
    ├── dto/, event/
    └── ReconciliationServiceApplication.java
```

## Matching granularity: per leg, not per transaction

`recon_result_audit`'s own table comment says "one row per reconciled ledger
leg (or missing/orphan leg)" — so a `WITHDRAWAL`/`DEPOSIT` transaction expands
to **one** expected leg, and a `TRANSFER` expands to **two** (a `DEBIT` on
`debit_account_id` and a `CREDIT` on `credit_account_id`), each reconciled
and persisted independently:

| txn_type | expected legs |
|---|---|
| `WITHDRAWAL` | 1 × `DEBIT` on `debit_account_id` |
| `DEPOSIT` | 1 × `CREDIT` on `credit_account_id` |
| `TRANSFER` | 1 × `DEBIT` on `debit_account_id` + 1 × `CREDIT` on `credit_account_id` |

`ExpectedLeg.from(TransactionMaster)` does this expansion; `MatchingService`
then pairs each expected leg against actual `ledger_mutation_audit` rows
sharing its `(txn_id, mutation_type)` key.

## Matching rules (`ExceptionClassifier`)

Checked in order, first match wins:

1. No actual leg at all → `MISSING_LEDGER_ENTRY` (CRITICAL)
2. More than one actual leg for the same key → `DUPLICATE_ENTRY` (HIGH) — deliberately not blocked at the DB layer (see the comment on `ix_ledger_audit_txn_acct_type`), so this is exactly the anomaly recon exists to catch
3. Leg's `account_id` ≠ expected account → `ACCOUNT_MISMATCH` (HIGH)
4. `|mutation_amount delta| > recon.matching.amount-tolerance` (default **0.0000** — both sides are `NUMBER/NUMERIC(18,4)` copied verbatim, so an exact match is expected) → `AMOUNT_MISMATCH` (HIGH)
5. `ledger_mutation_audit.audit_state ≠ transaction_master.txn_status` → `STATUS_MISMATCH` (MEDIUM) — the two columns deliberately share the same `PENDING`/`COMMITTED`/`ROLLED_BACK` vocabulary, per your schema comment
6. Posting lag (`leg.created_at - txn.completed_at`) over `recon.matching.late-posting-threshold-seconds` (default 300s) → `LATE_POSTING` (LOW) — there's no separate `posted_at` column on `ledger_mutation_audit`, so `created_at` doubles as the posting time since rows are append-only from insert
7. Otherwise → `MATCHED`

A ledger leg whose `(txn_id, mutation_type)` matched no expected leg at all —
wrong transaction, or a mutation_type that doesn't fit that txn_type — is
classified separately as `ORPHAN_LEDGER_ENTRY` (CRITICAL).

Transactions still `PENDING` are excluded from each window's query
(`TransactionMasterRepository.findAllInWindow`) — a transaction that hasn't
reached a final state yet may simply not have its ledger leg written yet,
which would otherwise read as a false `MISSING_LEDGER_ENTRY`.

All thresholds live in `application.yml` under `recon.matching.*` — nothing
is hardcoded in the classifier, and `ExceptionClassifierTest` covers every
branch (including the WITHDRAWAL/TRANSFER leg-expansion itself) without
touching a database.

## What's out of scope here

`fx_conversion_audit`, `fx_rate_cache`, and `notification_audit` all exist in
your Postgres schema but belong to ForEx Service and Notification Service —
recon doesn't read or write any of them. If you want recon to also verify FX
conversions (source/dest amounts against `fx_rate_cache` at time of
conversion) that would be a second matching pass, not covered here yet.

## Notes

`account_master`/`transaction_master` (Oracle) and `ledger_mutation_audit`/
`outbox_audit` (Postgres) are wired to your real DDL exactly — column names,
the debit/credit split on `transaction_master`, the shared status
vocabulary, and `outbox_audit`'s `aggregate_type`/`aggregate_id`/`event_type`
columns (not a generic `topic`/`message_key`).

Oracle username still defaults to `recon_ro` rather than reusing `ledger_app`
from accounts-service, since recon should have its own least-privilege,
SELECT-only DB user — you'll need to actually create that user/grants,
nothing here does it for you.

Matching is still done in-memory per window (`MatchingService`) rather than
pushed into a cross-database SQL join — fine at demo scale, revisit first if
`transaction_master` volume gets large.

## Running it

**Prerequisite**: run `01_oracle_main_db.sql` against your Oracle XE instance
and `01_postgres_audit_db.sql` against your Postgres instance first (in that
order doesn't matter between the two, since there's no cross-engine FK) —
this service does not create any schema itself.

```bash
docker build -t reconciliation-service .
# or, against a local Maven/JDK 21:
mvn spring-boot:run
```

Required env vars (defaults point at localhost, see `application.yml`):
`ORACLE_URL`, `ORACLE_USERNAME`, `ORACLE_PASSWORD`, `ORACLE_SCHEMA`,
`PG_URL`, `PG_USERNAME`, `PG_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/recon/runs` | Trigger an ad-hoc/end-of-day run over an explicit window |
| `GET`  | `/api/recon/runs` | Last 20 runs, newest first |
| `GET`  | `/api/recon/runs/{runId}` | One run's summary |
| `GET`  | `/api/recon/runs/{runId}/results` | Every matched/exception leg for that run |
| `GET`  | `/api/recon/breaks` | All open exceptions across all runs (the review queue) |

## Kafka topics published

- `reconciliation.discrepancy` — one event per `EXCEPTION` leg, for Notification Service to consume
- `reconciliation.run.completed` — one event per finished run (completed or failed)

Both are written to `outbox_audit` inside the same transaction as the recon
rows (`aggregate_type`/`aggregate_id`/`event_type`/`payload`), then relayed
to Kafka by `OutboxRelay` on a 2s poll, using `event_type` directly as the
Kafka topic name — so a Kafka outage never loses a discrepancy, it just
delays delivery.
