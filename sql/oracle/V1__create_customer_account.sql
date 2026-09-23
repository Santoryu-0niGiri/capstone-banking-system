-- Oracle XE master schema: customer + account
-- Executed automatically by gvenzl/oracle-xe container-entrypoint-initdb.d
-- Runs as the APP_USER (ledger_app) against XEPDB1.

ALTER SESSION SET CONTAINER = XEPDB1;

CREATE TABLE ledger_app.customer (
    cust_id        NUMBER(19)          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    f_name         VARCHAR2(100)        NOT NULL,
    l_name         VARCHAR2(100)        NOT NULL,
    email          VARCHAR2(255)        NOT NULL,
    phone_number   VARCHAR2(20)         NOT NULL,
    birthday       DATE                 NOT NULL,
    password_hash  VARCHAR2(255)        NOT NULL,
    created_at     TIMESTAMP            DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_customer_email UNIQUE (email)
);

CREATE TABLE ledger_app.account (
    acct_no        NUMBER(19)          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cust_id        NUMBER(19)          NOT NULL,
    acct_type      VARCHAR2(20)         NOT NULL,
    acct_status    VARCHAR2(20)         DEFAULT 'ACTIVE' NOT NULL,
    balance        NUMBER(18,4)         DEFAULT 0 NOT NULL,
    created_at     TIMESTAMP            DEFAULT SYSTIMESTAMP NOT NULL,
    version        NUMBER(19)           DEFAULT 0 NOT NULL,
    CONSTRAINT fk_account_customer FOREIGN KEY (cust_id) REFERENCES ledger_app.customer (cust_id),
    CONSTRAINT ck_account_type CHECK (acct_type IN ('SAVINGS', 'CHECKING', 'WALLET')),
    CONSTRAINT ck_account_status CHECK (acct_status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_account_balance_nonneg CHECK (balance >= 0)
);

CREATE INDEX idx_account_cust_id ON ledger_app.account (cust_id);
