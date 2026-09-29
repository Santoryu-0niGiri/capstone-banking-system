-- =====================================================================
-- POSTGRESQL 15+ - IMMUTABLE AUDIT DB
-- Tables: notification_audit, ledger_mutation_audit, recon_run_audit,
--         recon_result_audit
--
-- Aligned to CAPSTONE FSE: Core Retail Ledger & Balance Mutation Engine
-- account_id / txn_id here reference account_master /
-- transaction_master in the separate Oracle XE 21c database, so no
-- cross-engine FK is declared for those columns -- indexed instead.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------------------------------------------------------------
-- NOTIFICATION_AUDIT
-- ---------------------------------------------------------------------
CREATE TABLE notification_audit (
    notif_id     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id  VARCHAR(36)   NOT NULL,
    message      TEXT          NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_audit_status
        CHECK (status IN ('PENDING','SENT','FAILED'))
);

CREATE INDEX ix_notification_audit_customer_id ON notification_audit (customer_id);
CREATE INDEX ix_notification_audit_created_at ON notification_audit (created_at);

COMMENT ON TABLE notification_audit IS 'Downstream alert, decoupled from the sync mutate path via Kafka/RabbitMQ (spec Section D)';

-- ---------------------------------------------------------------------
-- LEDGER_MUTATION_AUDIT
-- The table named directly in the spec (Section C): "an append-only
-- transaction line". Enforced as truly immutable below -- no UPDATE or
-- DELETE is permitted once a row lands, only INSERT.
-- ---------------------------------------------------------------------
CREATE TABLE ledger_mutation_audit (
    mutation_uuid    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    txn_id           VARCHAR(36)    NOT NULL,
    account_id       VARCHAR(36)    NOT NULL,
    -- NUMERIC(18,4): 14 integer digits + 4 fraction digits, matching
    -- the Oracle-side @Digits(integer=14, fraction=4) constraint so the
    -- audit leg can never disagree with the master write on precision.
    mutation_amount  NUMERIC(18,4)  NOT NULL,
    currency_code    VARCHAR(3)        NOT NULL,
    mutation_type    VARCHAR(10)    NOT NULL,
    txn_type         VARCHAR(30)    NOT NULL,
    audit_state      VARCHAR(20)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_ledger_audit_mutation_type
        CHECK (mutation_type IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_ledger_audit_txn_type
        CHECK (txn_type IN ('WITHDRAWAL','DEPOSIT','TRANSFER')),
    -- kept in lockstep with transaction_master.txn_status (Oracle side):
    -- reconciliation's STATUS_MISMATCH check depends on both tables
    -- speaking the same vocabulary.
    CONSTRAINT ck_ledger_audit_state
        CHECK (audit_state IN ('PENDING','COMMITTED','ROLLED_BACK')),
    -- mirrors @Positive from the controller layer.
    CONSTRAINT ck_ledger_audit_amount_positive
        CHECK (mutation_amount > 0),
    CONSTRAINT ck_ledger_audit_currency_code
        CHECK (char_length(btrim(currency_code)) = 3)
);

-- Not a UNIQUE constraint on purpose: a second (txn_id, account_id,
-- mutation_type) row here is exactly the duplicate-posting anomaly a
-- reconciliation pass is designed to catch, so it must not be blocked
-- at the DB layer. Indexed instead for fast duplicate-detection queries.
CREATE INDEX ix_ledger_audit_txn_acct_type ON ledger_mutation_audit (txn_id, account_id, mutation_type);
CREATE INDEX ix_ledger_audit_txn_id ON ledger_mutation_audit (txn_id);
CREATE INDEX ix_ledger_audit_created_at ON ledger_mutation_audit (created_at);

COMMENT ON TABLE ledger_mutation_audit IS 'Immutable, append-only double-entry ledger posting, one row per debit/credit leg';

-- Enforce true immutability: block UPDATE/DELETE at the DB layer.
-- If Postgres is offline for the write itself, that failure surfaces
-- to the Spring @Transactional service, which must roll back Oracle
-- and raise LedgerPersistenceException (spec Section C, Data Drift
-- Remediation) -- this trigger guards the row once it exists.
CREATE OR REPLACE FUNCTION fn_ledger_mutation_audit_append_only()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_mutation_audit is append-only: % not permitted on mutation_uuid %',
        TG_OP, COALESCE(OLD.mutation_uuid, NULL);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_mutation_audit_append_only
    BEFORE UPDATE OR DELETE ON ledger_mutation_audit
    FOR EACH ROW EXECUTE FUNCTION fn_ledger_mutation_audit_append_only();

-- ---------------------------------------------------------------------
-- RECON_RUN_AUDIT
-- ---------------------------------------------------------------------
CREATE TABLE recon_run_audit (
    run_id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_started_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    run_completed_at    TIMESTAMPTZ,
    window_start        TIMESTAMPTZ   NOT NULL,
    window_end          TIMESTAMPTZ   NOT NULL,
    total_txn_checked   INTEGER       NOT NULL DEFAULT 0,
    total_matched       INTEGER       NOT NULL DEFAULT 0,
    total_exceptions    INTEGER       NOT NULL DEFAULT 0,
    run_status          VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
    CONSTRAINT ck_recon_run_audit_status
        CHECK (run_status IN ('RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_recon_run_audit_window
        CHECK (window_end >= window_start),
    CONSTRAINT ck_recon_run_audit_completed
        CHECK (run_completed_at IS NULL OR run_completed_at >= run_started_at),
    CONSTRAINT ck_recon_run_audit_counts
        CHECK (total_matched + total_exceptions <= total_txn_checked)
);

CREATE INDEX ix_recon_run_audit_started_at ON recon_run_audit (run_started_at);
CREATE INDEX ix_recon_run_audit_status ON recon_run_audit (run_status);

COMMENT ON TABLE recon_run_audit IS 'One row per reconciliation batch execution';

-- ---------------------------------------------------------------------
-- RECON_RESULT_AUDIT
-- ---------------------------------------------------------------------
CREATE TABLE recon_result_audit (
    result_id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id                   UUID           NOT NULL,
    txn_id                   VARCHAR(36)    NOT NULL,
    account_id               VARCHAR(36),
    mutation_uuid             UUID,
    duplicate_mutation_uuid   UUID,
    recon_status             VARCHAR(20)    NOT NULL,
    exception_type           VARCHAR(30),
    expected_currency_code   VARCHAR(3),
    actual_currency_code     VARCHAR(3),
    expected_amount          NUMERIC(18,4),
    actual_amount             NUMERIC(18,4),
    variance_amount           NUMERIC(18,4) GENERATED ALWAYS AS (
        CASE WHEN expected_currency_code IS NOT NULL
                   AND expected_currency_code = actual_currency_code
             THEN actual_amount - expected_amount
             ELSE NULL
        END
    ) STORED,
    txn_status                VARCHAR(20),
    ledger_audit_state         VARCHAR(20),
    txn_completed_at           TIMESTAMPTZ,
    ledger_posted_at           TIMESTAMPTZ,
    posting_lag_seconds        INTEGER,
    severity                   VARCHAR(10)    NOT NULL DEFAULT 'LOW',
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_recon_result_audit_run FOREIGN KEY (run_id)
        REFERENCES recon_run_audit (run_id),
    CONSTRAINT fk_recon_result_audit_ledger FOREIGN KEY (mutation_uuid)
        REFERENCES ledger_mutation_audit (mutation_uuid),
    CONSTRAINT fk_recon_result_audit_dup_ledger FOREIGN KEY (duplicate_mutation_uuid)
        REFERENCES ledger_mutation_audit (mutation_uuid),
    CONSTRAINT ck_recon_result_audit_status
        CHECK (recon_status IN ('MATCHED','EXCEPTION')),
    CONSTRAINT ck_recon_result_audit_exception_type
        CHECK (exception_type IS NULL OR exception_type IN (
            'MISSING_LEDGER_ENTRY','ORPHAN_LEDGER_ENTRY','AMOUNT_MISMATCH',
            'ACCOUNT_MISMATCH','STATUS_MISMATCH','DUPLICATE_ENTRY','LATE_POSTING',
            'CURRENCY_MISMATCH'
        )),
    CONSTRAINT ck_recon_result_audit_exception_required
        CHECK (
            (recon_status = 'MATCHED' AND exception_type IS NULL)
            OR (recon_status = 'EXCEPTION' AND exception_type IS NOT NULL)
        ),
    CONSTRAINT ck_recon_result_audit_severity
        CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX ix_recon_result_audit_run_id ON recon_result_audit (run_id);
CREATE INDEX ix_recon_result_audit_txn_id ON recon_result_audit (txn_id);
CREATE INDEX ix_recon_result_audit_status ON recon_result_audit (recon_status, exception_type);
CREATE INDEX ix_recon_result_audit_created_at ON recon_result_audit (created_at);

COMMENT ON TABLE recon_result_audit IS 'One row per reconciled ledger leg (or missing/orphan leg) within a recon_run_audit';


-- ---------------------------------------------------------------------
-- FX_CONVERSION_AUDIT
-- ---------------------------------------------------------------------
CREATE TABLE fx_conversion_audit (
    conversion_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    txn_id              VARCHAR(36)   NOT NULL,
    source_currency     CHAR(3)       NOT NULL,
    dest_currency       CHAR(3)       NOT NULL,
    source_amount       NUMERIC(18,4) NOT NULL,
    fx_rate             NUMERIC(18,8) NOT NULL,
    dest_amount         NUMERIC(18,4) NOT NULL,
    conversion_status   VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_fx_conversion_status
        CHECK (conversion_status IN ('COMPLETED','FAILED')),
    -- mirrors fx_rate_cache's own checks below, applied to the amounts
    -- actually posted for this conversion rather than the cached rate.
    CONSTRAINT ck_fx_conversion_positive
        CHECK (source_amount > 0 AND fx_rate > 0 AND dest_amount > 0),
    CONSTRAINT ck_fx_conversion_diff_currency
        CHECK (source_currency <> dest_currency)
);
 
CREATE INDEX ix_fx_conversion_audit_txn_id ON fx_conversion_audit (txn_id);
 
COMMENT ON TABLE fx_conversion_audit IS 'One row per cross-currency conversion applied to a TRANSFER transaction';
 
-- ---------------------------------------------------------------------
-- FX_RATE_CACHE (owned by ForEx Service)
-- Moved here from oracle_main_db.sql: it used PostgreSQL-only types
-- (UUID, TIMESTAMPTZ, uuid_generate_v4()) and its own comment already
-- identified it as Postgres-side, so it could not run against Oracle.
-- Populated by a scheduled poll of api.frankfurter.dev, NOT read live
-- from the external API during transaction processing -- keeps the
-- async settlement path independent of third-party API uptime/latency.
-- ---------------------------------------------------------------------
CREATE TABLE fx_rate_cache (
    rate_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    base_currency    CHAR(3)        NOT NULL,
    quote_currency   CHAR(3)        NOT NULL,
    rate             NUMERIC(18,8)  NOT NULL,
    fetched_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    source           VARCHAR(50)    NOT NULL DEFAULT 'frankfurter.dev',
    CONSTRAINT uq_fx_rate_cache_pair UNIQUE (base_currency, quote_currency),
    CONSTRAINT ck_fx_rate_cache_positive CHECK (rate > 0),
    CONSTRAINT ck_fx_rate_cache_diff_currency CHECK (base_currency <> quote_currency)
);
 
CREATE INDEX ix_fx_rate_cache_fetched_at ON fx_rate_cache (fetched_at);
 
COMMENT ON TABLE fx_rate_cache IS
    'Locally cached FX rates, refreshed on a schedule from api.frankfurter.dev. '
    'fx_conversion_audit.fx_rate is copied from here at conversion time, not looked up live.';


-- ---------------------------------------------------------------------
-- OUTBOX_AUDIT (PostgreSQL, owned by Transaction Service)
-- Written in the SAME local transaction as the ledger_mutation_audit
-- insert it accompanies -- a separate poller/relay reads PENDING rows
-- and publishes to Kafka, then marks them PUBLISHED. This is what
-- makes event publishing atomic with the audit write it reports on,
-- without needing a distributed transaction across Kafka + Postgres.
-- ---------------------------------------------------------------------
CREATE TABLE outbox_audit (
    outbox_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_service  VARCHAR(50)   NOT NULL DEFAULT 'transaction-service',
    aggregate_type  VARCHAR(30)   NOT NULL,   -- e.g. 'TRANSACTION', 'FOREX', 'LEDGER_MUTATION'
    aggregate_id    VARCHAR(36)   NOT NULL,   -- txn_id
    event_type      VARCHAR(60)   NOT NULL,   -- e.g. 'transaction.completed', 'forex.conversion.requested'
    payload         JSONB         NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT ck_outbox_audit_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
);

CREATE INDEX ix_outbox_audit_status ON outbox_audit (status, created_at);

-- -- =====================================================================
-- -- SEED DATA (PostgreSQL 15+)
-- -- Pre-populated FX rate cache, opening ledger mutation audits, and notifications
-- -- =====================================================================

-- -- 1. FX Rate Cache (api.frankfurter.dev canonical and cross pairs)
-- INSERT INTO fx_rate_cache (base_currency, quote_currency, rate, fetched_at, source)
-- VALUES
--     ('USD', 'PHP', 58.50000000, now(), 'frankfurter.dev'),
--     ('PHP', 'USD', 0.01709402, now(), 'frankfurter.dev'),
--     ('EUR', 'PHP', 63.25000000, now(), 'frankfurter.dev'),
--     ('PHP', 'EUR', 0.01581028, now(), 'frankfurter.dev'),
--     ('EUR', 'USD', 1.08119658, now(), 'frankfurter.dev'),
--     ('USD', 'EUR', 0.92490119, now(), 'frankfurter.dev'),
--     ('GBP', 'PHP', 74.10000000, now(), 'frankfurter.dev'),
--     ('PHP', 'GBP', 0.01349528, now(), 'frankfurter.dev'),
--     ('SGD', 'PHP', 43.80000000, now(), 'frankfurter.dev'),
--     ('PHP', 'SGD', 0.02283105, now(), 'frankfurter.dev')
-- ON CONFLICT (base_currency, quote_currency) DO UPDATE
-- SET rate = EXCLUDED.rate, fetched_at = EXCLUDED.fetched_at;

-- -- 2. Ledger Mutation Audits (Opening balance credits matching Oracle transaction_master)
-- INSERT INTO ledger_mutation_audit (txn_id, account_id, mutation_amount, mutation_type, txn_type, audit_state, created_at)
-- VALUES
--     ('txn-init-juan-php', 'acct-juan-php-01', 50000.0000, 'CREDIT', 'DEPOSIT', 'COMMITTED', now()),
--     ('txn-init-juan-usd', 'acct-juan-usd-01', 1500.0000, 'CREDIT', 'DEPOSIT', 'COMMITTED', now()),
--     ('txn-init-maria-php', 'acct-maria-php-01', 120000.0000, 'CREDIT', 'DEPOSIT', 'COMMITTED', now()),
--     ('txn-init-maria-eur', 'acct-maria-eur-01', 800.0000, 'CREDIT', 'DEPOSIT', 'COMMITTED', now());

-- -- 3. Initial Notifications
-- INSERT INTO notification_audit (customer_id, message, status, created_at)
-- VALUES
--     ('cust-user-001', 'Welcome to LedgerBank! Your savings and checking accounts are active and funded.', 'SENT', now()),
--     ('cust-user-002', 'Welcome to LedgerBank! Your savings and wallet accounts are active and funded.', 'SENT', now());

