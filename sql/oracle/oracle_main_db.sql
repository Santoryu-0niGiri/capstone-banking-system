-- =====================================================================
-- ORACLE XE 21c - MASTER DB
-- Tables: customer_master, customer_balance_master, app_user_master,
--         transaction_master
--
-- Aligned to CAPSTONE FSE: Core Retail Ledger & Balance Mutation Engine
-- table/column names follow the spec's snake_case JPA @Column style so
-- unquoted identifiers resolve consistently against Hibernate defaults.
-- =====================================================================

ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = LEDGER_APP;

-- ---------------------------------------------------------------------
-- CUSTOMER_MASTER
-- ---------------------------------------------------------------------
CREATE TABLE customer_master (
    customer_id   VARCHAR2(36)   NOT NULL,
    first_name    VARCHAR2(100)  NOT NULL,
    last_name     VARCHAR2(100)  NOT NULL,
    email         VARCHAR2(255)  NOT NULL,
    contact_no    VARCHAR2(20),
    birth_date    DATE,
    created_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    created_by    VARCHAR2(50)   NOT NULL,
    updated_at    TIMESTAMP,
    updated_by    VARCHAR2(50),
    CONSTRAINT pk_customer_master PRIMARY KEY (customer_id),
    CONSTRAINT uq_customer_master_email UNIQUE (email),
    CONSTRAINT ck_customer_master_email CHECK (email LIKE '%@%.%')
);

COMMENT ON TABLE customer_master IS 'Retail customer master record';

-- ---------------------------------------------------------------------
-- CUSTOMER_BALANCE_MASTER
-- The table named directly in the spec (Section B/C). Holds the live,
-- directly-updated balance state that /api/v1/ledger/mutate reads and
-- writes under a pessimistic row lock.
-- ---------------------------------------------------------------------
CREATE TABLE customer_balance_master (
    account_id      VARCHAR2(36)   NOT NULL,
    customer_id     VARCHAR2(36)   NOT NULL,
    account_type    VARCHAR2(30)   NOT NULL,
    currency_code   CHAR(3)        NOT NULL,
    account_status  VARCHAR2(20)   DEFAULT 'ACTIVE' NOT NULL,
    -- NUMBER(18,4): 14 integer digits + 4 fraction digits, mirrors the
    -- controller-level @Digits(integer=14, fraction=4) constraint on
    -- mutation_amount so the DB can never silently truncate a value
    -- the API already accepted.
    balance_amount  NUMBER(18,4)   DEFAULT 0 NOT NULL,
    created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    created_by      VARCHAR2(50)   NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR2(50),
    version         NUMBER(19)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_customer_balance_master PRIMARY KEY (account_id),
    CONSTRAINT fk_balance_master_customer FOREIGN KEY (customer_id)
        REFERENCES customer_master (customer_id),
    CONSTRAINT ck_balance_master_acct_type CHECK (account_type IN ('SAVINGS','CHECKING','WALLET')),
    CONSTRAINT ck_balance_master_status CHECK (account_status IN ('ACTIVE','FROZEN','CLOSED')),
    CONSTRAINT ck_balance_master_nonneg CHECK (balance_amount >= 0)
);

CREATE INDEX ix_balance_master_customer_id ON customer_balance_master (customer_id);

COMMENT ON TABLE customer_balance_master IS
    'Live balance state, row-locked via @Lock(LockModeType.PESSIMISTIC_WRITE) -> SELECT balance_amount FROM customer_balance_master WHERE account_id = ? FOR UPDATE';

-- ---------------------------------------------------------------------
-- APP_USER_MASTER
-- ---------------------------------------------------------------------
CREATE TABLE app_user_master (
    user_id        VARCHAR2(36)   NOT NULL,
    customer_id    VARCHAR2(36)   NOT NULL,
    username       VARCHAR2(50)   NOT NULL,
    password_hash  VARCHAR2(255)  NOT NULL,
    active_status  VARCHAR2(20)   DEFAULT 'ACTIVE' NOT NULL,
    created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    created_by     VARCHAR2(50)   NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     VARCHAR2(50),
    CONSTRAINT pk_app_user_master PRIMARY KEY (user_id),
    CONSTRAINT fk_app_user_master_customer FOREIGN KEY (customer_id)
        REFERENCES customer_master (customer_id),
    CONSTRAINT uq_app_user_master_username UNIQUE (username),
    CONSTRAINT ck_app_user_master_active_status CHECK (active_status IN ('ACTIVE','SUSPENDED','LOCKED','DISABLED'))
);

CREATE INDEX ix_app_user_master_customer_id ON app_user_master (customer_id);

COMMENT ON TABLE app_user_master IS 'Login credentials, validated by the stateless JWT filter (Day 34)';

-- ---------------------------------------------------------------------
-- TRANSACTION_MASTER
-- One row per /api/v1/ledger/mutate request. mutation_amount carries
-- the same precision/positivity constraints enforced at the controller
-- (@Digits(integer=14, fraction=4), @Positive) as a defense-in-depth
-- check, not a replacement for the JSR-380 validation.
-- ---------------------------------------------------------------------
CREATE TABLE transaction_master (
    txn_id             VARCHAR2(36)   NOT NULL,
    txn_type           VARCHAR2(30)   NOT NULL,
    -- Nullable: only WITHDRAWAL and DEPOSIT touch a single account, so
    -- exactly one side is populated for those; only TRANSFER involves
    -- both. Enforced below by ck_txn_debit_credit_by_type.
    debit_account_id   VARCHAR2(36),
    credit_account_id  VARCHAR2(36),
    mutation_amount    NUMBER(18,4)   NOT NULL,
    txn_status         VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
    initiated_at       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at       TIMESTAMP,
    created_at         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    created_by         VARCHAR2(50)   NOT NULL,
    updated_at         TIMESTAMP,
    updated_by         VARCHAR2(50),
    CONSTRAINT pk_transaction_master PRIMARY KEY (txn_id),
    CONSTRAINT fk_txn_debit_account FOREIGN KEY (debit_account_id)
        REFERENCES customer_balance_master (account_id),
    CONSTRAINT fk_txn_credit_account FOREIGN KEY (credit_account_id)
        REFERENCES customer_balance_master (account_id),
    CONSTRAINT ck_txn_type CHECK (txn_type IN ('WITHDRAWAL','DEPOSIT','TRANSFER')),
    CONSTRAINT ck_txn_status CHECK (txn_status IN ('PENDING','COMMITTED','ROLLED_BACK')),
    -- mirrors controller-level @Positive: negative amounts are blocked
    -- before they ever reach the persistence layer.
    CONSTRAINT ck_txn_amount_positive CHECK (mutation_amount > 0),
    -- NULL-safe: if either side is null (withdrawal/deposit) this
    -- comparison evaluates to UNKNOWN, which Oracle treats as satisfied.
    -- It only actively guards against debit = credit on a TRANSFER.
    CONSTRAINT ck_txn_diff_accounts CHECK (debit_account_id <> credit_account_id),
    -- Which side is required/forbidden depends on txn_type:
    --   WITHDRAWAL -> debit only (money leaves one account)
    --   DEPOSIT    -> credit only (money enters one account)
    --   TRANSFER   -> both required (money moves between two accounts)
    CONSTRAINT ck_txn_debit_credit_by_type CHECK (
        (txn_type = 'WITHDRAWAL' AND debit_account_id  IS NOT NULL AND credit_account_id IS NULL)
        OR (txn_type = 'DEPOSIT'  AND credit_account_id IS NOT NULL AND debit_account_id  IS NULL)
        OR (txn_type = 'TRANSFER' AND debit_account_id  IS NOT NULL AND credit_account_id IS NOT NULL)
    ),
    CONSTRAINT ck_txn_completed_after CHECK (completed_at IS NULL OR completed_at >= initiated_at)
);

CREATE INDEX ix_txn_debit_account ON transaction_master (debit_account_id);
CREATE INDEX ix_txn_credit_account ON transaction_master (credit_account_id);
CREATE INDEX ix_txn_status ON transaction_master (txn_status);
CREATE INDEX ix_txn_completed_at ON transaction_master (completed_at);

COMMENT ON TABLE transaction_master IS
    'Requested balance mutations. On PostgreSQL audit-write failure, the owning service must roll back this row and throw LedgerPersistenceException to prevent an un-audited state change (spec Section C).';
