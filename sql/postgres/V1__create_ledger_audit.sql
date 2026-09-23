-- PostgreSQL audit schema: ledger_mutation_audit
-- Executed automatically by the postgres image's docker-entrypoint-initdb.d

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE ledger_mutation_audit (
    txn_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    acct_id          BIGINT           NOT NULL,
    mutation_amount  NUMERIC(18,4)    NOT NULL,
    txn_type         VARCHAR(20)      NOT NULL CHECK (txn_type IN ('DEBIT', 'CREDIT', 'TRANSFER_OUT', 'TRANSFER_IN')),
    counterparty_acct_id BIGINT,
    balance_after    NUMERIC(18,4)    NOT NULL,
    timestamp        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    audit_state      VARCHAR(20)      NOT NULL CHECK (audit_state IN ('RECORDED', 'REVERSED')),
    idempotency_key  VARCHAR(100)
);

CREATE INDEX idx_ledger_audit_acct_id ON ledger_mutation_audit (acct_id);
CREATE INDEX idx_ledger_audit_timestamp ON ledger_mutation_audit (timestamp);
