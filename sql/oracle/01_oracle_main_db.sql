-- CONNECT ledger_app/LedgerAppPass123@//localhost:1521/XEPDB1

-- =====================================================================
-- ORACLE XE 21c - MASTER DB
-- Tables: customer_master, account_master, app_user_master,
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
-- ACCOUNT_MASTER
-- The table named directly in the spec (Section B/C). Holds the live,
-- directly-updated balance state that /api/v1/ledger/mutate reads and
-- writes under a pessimistic row lock.
-- ---------------------------------------------------------------------
CREATE TABLE account_master (
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
    version         NUMBER(19)      DEFAULT 0 NOT NULL,
    CONSTRAINT pk_account_master PRIMARY KEY (account_id),
    CONSTRAINT fk_account_master_customer FOREIGN KEY (customer_id)
        REFERENCES customer_master (customer_id),
    CONSTRAINT ck_account_master_acct_type CHECK (account_type IN ('SAVINGS','CHECKING','WALLET')),
    CONSTRAINT ck_account_master_status CHECK (account_status IN ('ACTIVE','FROZEN','CLOSED')),
    CONSTRAINT ck_account_master_nonneg CHECK (balance_amount >= 0)
);

CREATE INDEX ix_account_master_customer_id ON account_master (customer_id);

COMMENT ON TABLE account_master IS
    'Live balance state, row-locked via @Lock(LockModeType.PESSIMISTIC_WRITE) '
    '-> SELECT balance_amount FROM account_master WHERE account_id = ? FOR UPDATE';

-- ---------------------------------------------------------------------
-- APP_USER_MASTER
-- ---------------------------------------------------------------------
CREATE TABLE app_user_master (
    user_id        VARCHAR2(36)   NOT NULL,
    customer_id    VARCHAR2(36)   NOT NULL,
    username       VARCHAR2(50)   NOT NULL,
    password_hash  VARCHAR2(255)  NOT NULL,
    role           VARCHAR2(20)   DEFAULT 'CUSTOMER' NOT NULL,
    active_status  VARCHAR2(20)   DEFAULT 'ACTIVE' NOT NULL,
    created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    created_by     VARCHAR2(50)   NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     VARCHAR2(50),
    CONSTRAINT pk_app_user_master PRIMARY KEY (user_id),
    CONSTRAINT fk_app_user_master_customer FOREIGN KEY (customer_id)
        REFERENCES customer_master (customer_id),
    CONSTRAINT uq_app_user_master_username UNIQUE (username),
    CONSTRAINT ck_app_user_master_active_status CHECK (active_status IN ('ACTIVE','SUSPENDED','LOCKED','DISABLED')),
    CONSTRAINT ck_app_user_master_role CHECK (role IN ('CUSTOMER','ADMIN'))
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
    is_cross_currency  VARCHAR2(1)    DEFAULT 'N' NOT NULL,
    fx_rate            NUMBER(18,8),
    dest_amount        NUMBER(18,4),
    txn_status         VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
    initiated_at       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at       TIMESTAMP,
    created_at         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    created_by         VARCHAR2(50)   NOT NULL,
    updated_at         TIMESTAMP,
    updated_by         VARCHAR2(50),
    CONSTRAINT pk_transaction_master PRIMARY KEY (txn_id),
    CONSTRAINT fk_txn_debit_account FOREIGN KEY (debit_account_id)
        REFERENCES account_master (account_id),
    CONSTRAINT fk_txn_credit_account FOREIGN KEY (credit_account_id)
        REFERENCES account_master (account_id),
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

-- =====================================================================
-- OUTBOX_MASTER (Oracle XE 21c) — oracle_main_db.sql
-- Owned by accounts-service. Written in the same local Oracle transaction
-- as account_master mutations (account creation, balance updates, cross-currency
-- settlement), guaranteeing atomic event staging.
-- Relayed asynchronously to Kafka via OutboxMasterRelayService.
-- =====================================================================
CREATE TABLE outbox_master (
    outbox_id       VARCHAR2(36)  NOT NULL,   -- app-generated, same convention as txn_id/account_id
    source_service  VARCHAR2(50)  DEFAULT 'accounts-service' NOT NULL,
    aggregate_type  VARCHAR2(30)  NOT NULL,   -- e.g. 'ACCOUNT', 'CROSS_CURRENCY'
    aggregate_id    VARCHAR2(36)  NOT NULL,   -- account_id or txn_id
    event_type      VARCHAR2(60)  NOT NULL,   -- e.g. 'account.created', 'balance.updated', 'crosscurrency.settlement.completed'
    payload         JSON          NOT NULL,
    status          VARCHAR2(20)  DEFAULT 'PENDING' NOT NULL,
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    published_at    TIMESTAMP,
    CONSTRAINT pk_outbox_master PRIMARY KEY (outbox_id),
    CONSTRAINT ck_outbox_master_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
);

CREATE INDEX ix_outbox_master_status ON outbox_master (status, created_at);

COMMENT ON TABLE outbox_master IS
    'Transactional outbox for accounts-service mutations (account creations, balance updates, and cross-currency settlement). Polled by OutboxMasterRelayService.';

-- =====================================================================
-- SEED DATA (Oracle XE 21c)
-- Standard personas for immediate end-to-end testing across UI & Backend:
-- 1. Admin Persona:
--    - username: admin@ledgerbank.com
--    - password: admin123 (BCrypt hash: $2a$10$Wj5Cqd6hjqjppB6IItquHOun3MVKhJZShmIPi0SmAJUDygytdipN6)
--    - role: ADMIN
-- 2. Customer 1 (Juan Dela Cruz):
--    - username: juan.delacruz@example.com
--    - password: password123 (BCrypt hash: $2a$10$yPxSmEaD/2O6lRX.xPlL/OrFxdcv5MGklq.ExJ/IYcmOD.TA2eQB.)
--    - accounts:
--        * acct-juan-php-01 (SAVINGS, PHP, 50,000.0000)
--        * acct-juan-usd-01 (CHECKING, USD, 1,500.0000)
-- 3. Customer 2 (Maria Santos):
--    - username: maria.santos@example.com
--    - password: password123 (BCrypt hash: $2a$10$yPxSmEaD/2O6lRX.xPlL/OrFxdcv5MGklq.ExJ/IYcmOD.TA2eQB.)
--    - accounts:
--        * acct-maria-php-01 (SAVINGS, PHP, 120,000.0000)
--        * acct-maria-eur-01 (WALLET, EUR, 800.0000)
-- =====================================================================

-- 1. Customers
INSERT INTO customer_master (customer_id, first_name, last_name, email, contact_no, birth_date, created_at, created_by)
VALUES ('cust-admin-001', 'System', 'Admin', 'admin@ledgerbank.com', '+639170000000', TO_DATE('1985-01-01', 'YYYY-MM-DD'), SYSTIMESTAMP, 'SYSTEM');

INSERT INTO customer_master (customer_id, first_name, last_name, email, contact_no, birth_date, created_at, created_by)
VALUES ('cust-user-001', 'Juan', 'Dela Cruz', 'juan.delacruz@example.com', '+639171234567', TO_DATE('1990-05-15', 'YYYY-MM-DD'), SYSTIMESTAMP, 'SYSTEM');

INSERT INTO customer_master (customer_id, first_name, last_name, email, contact_no, birth_date, created_at, created_by)
VALUES ('cust-user-002', 'Maria', 'Santos', 'maria.santos@example.com', '+639189876543', TO_DATE('1992-08-20', 'YYYY-MM-DD'), SYSTIMESTAMP, 'SYSTEM');

-- 2. App Users (Credentials)
INSERT INTO app_user_master (user_id, customer_id, username, password_hash, role, active_status, created_at, created_by)
VALUES ('usr-admin-001', 'cust-admin-001', 'admin@ledgerbank.com', '$2a$10$Wj5Cqd6hjqjppB6IItquHOun3MVKhJZShmIPi0SmAJUDygytdipN6', 'ADMIN', 'ACTIVE', SYSTIMESTAMP, 'SYSTEM');

INSERT INTO app_user_master (user_id, customer_id, username, password_hash, role, active_status, created_at, created_by)
VALUES ('usr-user-001', 'cust-user-001', 'juan.delacruz@example.com', '$2a$10$yPxSmEaD/2O6lRX.xPlL/OrFxdcv5MGklq.ExJ/IYcmOD.TA2eQB.', 'CUSTOMER', 'ACTIVE', SYSTIMESTAMP, 'SYSTEM');

INSERT INTO app_user_master (user_id, customer_id, username, password_hash, role, active_status, created_at, created_by)
VALUES ('usr-user-002', 'cust-user-002', 'maria.santos@example.com', '$2a$10$yPxSmEaD/2O6lRX.xPlL/OrFxdcv5MGklq.ExJ/IYcmOD.TA2eQB.', 'CUSTOMER', 'ACTIVE', SYSTIMESTAMP, 'SYSTEM');

-- 3. Accounts
INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_at, created_by)
VALUES ('acct-juan-php-01', 'cust-user-001', 'SAVINGS', 'PHP', 'ACTIVE', 50000.0000, SYSTIMESTAMP, 'SYSTEM');

INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_at, created_by)
VALUES ('acct-juan-usd-01', 'cust-user-001', 'CHECKING', 'USD', 'ACTIVE', 1500.0000, SYSTIMESTAMP, 'SYSTEM');

INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_at, created_by)
VALUES ('acct-maria-php-01', 'cust-user-002', 'SAVINGS', 'PHP', 'ACTIVE', 120000.0000, SYSTIMESTAMP, 'SYSTEM');

INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_at, created_by)
VALUES ('acct-maria-eur-01', 'cust-user-002', 'WALLET', 'EUR', 'ACTIVE', 800.0000, SYSTIMESTAMP, 'SYSTEM');

-- 4. Initial Transactions (Opening Balance Deposits)
INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, is_cross_currency, txn_status, initiated_at, completed_at, created_at, created_by)
VALUES ('txn-init-juan-php', 'DEPOSIT', NULL, 'acct-juan-php-01', 50000.0000, 'N', 'COMMITTED', SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP, 'SYSTEM');

INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, is_cross_currency, txn_status, initiated_at, completed_at, created_at, created_by)
VALUES ('txn-init-juan-usd', 'DEPOSIT', NULL, 'acct-juan-usd-01', 1500.0000, 'N', 'COMMITTED', SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP, 'SYSTEM');

INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, is_cross_currency, txn_status, initiated_at, completed_at, created_at, created_by)
VALUES ('txn-init-maria-php', 'DEPOSIT', NULL, 'acct-maria-php-01', 120000.0000, 'N', 'COMMITTED', SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP, 'SYSTEM');

INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, is_cross_currency, txn_status, initiated_at, completed_at, created_at, created_by)
VALUES ('txn-init-maria-eur', 'DEPOSIT', NULL, 'acct-maria-eur-01', 800.0000, 'N', 'COMMITTED', SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP, 'SYSTEM');

COMMIT;