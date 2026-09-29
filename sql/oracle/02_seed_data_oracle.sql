-- =====================================================================
-- ORACLE XE 21c - SEED DATA
-- 02_seed_data.sql - runs after 01_oracle_main_db.sql (alphabetical
-- init-script order), so rename your existing schema file to
-- 01_oracle_main_db.sql if it isn't already.
--
-- Part 1: 3 app_user_master logins
--   admin@ledgerbank.com       / admin123      (ADMIN, no accounts)
--   juan.delacruz@example.com  / password123   (CUSTOMER, 2 accounts)
--   maria.santos@example.com   / password123   (CUSTOMER, 2 accounts)
--
-- Part 2: 5 COMMITTED transactions across those accounts, covering all
-- three txn_type values. Each txn_id here has a matching
-- ledger_mutation_audit row (or two, for the TRANSFERs) in
-- sql/postgres/02_seed_data.sql, using the SAME txn_id and the SAME
-- mutation_amount, so a reconciliation-service run over 2026-09-27
-- comes back 100% MATCHED, zero exceptions.
--
-- password_hash values are BCrypt ($2b$, cost 12) - compatible with
-- Spring Security's default BCryptPasswordEncoder. Regenerate these if
-- your login-service pins a different strength/version; see note at
-- the bottom of this file for how.
-- =====================================================================

ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = LEDGER_APP;

-- =====================================================================
-- PART 1: USERS, CUSTOMERS, ACCOUNTS
-- =====================================================================

-- ---------------------------------------------------------------------
-- CUSTOMER_MASTER
-- admin still gets a customer_master row (app_user_master.customer_id
-- is NOT NULL / FK'd to it) even though it has no bank accounts -
-- "no account" is enforced by simply not inserting into account_master
-- for this customer_id, not by skipping customer_master.
-- ---------------------------------------------------------------------
INSERT INTO customer_master (customer_id, first_name, last_name, email, contact_no, birth_date, created_by)
VALUES ('2d49c23b-5f65-48da-aa68-28b155dd4022', 'System', 'Administrator', 'admin@ledgerbank.com', NULL, NULL, 'seed');

INSERT INTO customer_master (customer_id, first_name, last_name, email, contact_no, birth_date, created_by)
VALUES ('7bd20304-dd6a-4e45-850b-58df8b535be4', 'Juan', 'Dela Cruz', 'juan.delacruz@example.com', '+639171234567', DATE '1995-03-14', 'seed');

INSERT INTO customer_master (customer_id, first_name, last_name, email, contact_no, birth_date, created_by)
VALUES ('66e07d02-05c9-4deb-87d5-10e23cf5968f', 'Maria', 'Santos', 'maria.santos@example.com', '+639189876543', DATE '1998-11-02', 'seed');

-- ---------------------------------------------------------------------
-- APP_USER_MASTER
-- ---------------------------------------------------------------------
INSERT INTO app_user_master (user_id, customer_id, username, password_hash, role, active_status, created_by)
VALUES ('69aec640-58c2-41a0-ad68-856e7c9345cc', '2d49c23b-5f65-48da-aa68-28b155dd4022',
        'admin@ledgerbank.com', '$2b$12$k4MRn5BmsyCSbiVa6k8e0O/xp/YSne2/CQuvxVOKohZ86.VXAWkoW',
        'ADMIN', 'ACTIVE', 'seed');

INSERT INTO app_user_master (user_id, customer_id, username, password_hash, role, active_status, created_by)
VALUES ('5790bfd2-5c28-4b59-abe8-3f460099a264', '7bd20304-dd6a-4e45-850b-58df8b535be4',
        'juan.delacruz@example.com', '$2b$12$7c3QXl8O0IFVgBcH1ea2nOKPMKiVkP4x85RR2.w31/iHKwXYDwvre',
        'CUSTOMER', 'ACTIVE', 'seed');

INSERT INTO app_user_master (user_id, customer_id, username, password_hash, role, active_status, created_by)
VALUES ('a40e6a5f-72c4-4c1a-a298-815053f14b23', '66e07d02-05c9-4deb-87d5-10e23cf5968f',
        'maria.santos@example.com', '$2b$12$7c3QXl8O0IFVgBcH1ea2nOKPMKiVkP4x85RR2.w31/iHKwXYDwvre',
        'CUSTOMER', 'ACTIVE', 'seed');

-- ---------------------------------------------------------------------
-- ACCOUNT_MASTER
-- admin (customer_id 2d49c23b-...) intentionally gets NO rows here.
-- Opening balances below are the PRE-transaction amounts; Part 2 rolls
-- the net effect of the seeded transactions into these via UPDATE, so
-- by the end of this file balance_amount reflects the transactions.
-- ---------------------------------------------------------------------
INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_by)
VALUES ('3722f77f-3d1b-4d55-8f83-b71bac91d095', '7bd20304-dd6a-4e45-850b-58df8b535be4',
        'SAVINGS', 'PHP', 'ACTIVE', 15000.0000, 'seed');

INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_by)
VALUES ('a86c97cc-1ea5-4767-bd5b-148968354f9c', '7bd20304-dd6a-4e45-850b-58df8b535be4',
        'CHECKING', 'PHP', 'ACTIVE', 5000.0000, 'seed');

INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_by)
VALUES ('f95e7e52-3259-4b4d-ac66-b186a893cf66', '66e07d02-05c9-4deb-87d5-10e23cf5968f',
        'CHECKING', 'PHP', 'ACTIVE', 8000.0000, 'seed');

INSERT INTO account_master (account_id, customer_id, account_type, currency_code, account_status, balance_amount, created_by)
VALUES ('86cabb96-61d5-462a-b33b-5a6435649eb4', '66e07d02-05c9-4deb-87d5-10e23cf5968f',
        'WALLET', 'PHP', 'ACTIVE', 2500.0000, 'seed');

-- =====================================================================
-- PART 2: TRANSACTIONS
-- Account IDs used below, for reference:
--   Juan Savings   = 3722f77f-3d1b-4d55-8f83-b71bac91d095
--   Juan Checking  = a86c97cc-1ea5-4767-bd5b-148968354f9c
--   Maria Checking = f95e7e52-3259-4b4d-ac66-b186a893cf66
--   Maria Wallet   = 86cabb96-61d5-462a-b33b-5a6435649eb4
-- =====================================================================

-- ---------------------------------------------------------------------
-- T1: DEPOSIT +2000.0000 into Juan Savings
-- ---------------------------------------------------------------------
INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, currency_code, dest_currency_code, txn_status, initiated_at, completed_at, created_by)
VALUES ('05da5006-2cc5-4dd0-87ab-aeca1f0f7ab1', 'DEPOSIT', NULL, '3722f77f-3d1b-4d55-8f83-b71bac91d095',
        2000.0000, 'PHP', NULL, 'COMMITTED',
        TIMESTAMP '2026-09-27 08:00:00', TIMESTAMP '2026-09-27 08:00:03', 'seed');

-- ---------------------------------------------------------------------
-- T2: WITHDRAWAL -500.0000 from Juan Checking
-- ---------------------------------------------------------------------
INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, currency_code, dest_currency_code, txn_status, initiated_at, completed_at, created_by)
VALUES ('b314f899-2fc0-4087-80e6-09e87ece0d49', 'WITHDRAWAL', 'a86c97cc-1ea5-4767-bd5b-148968354f9c', NULL,
        500.0000, 'PHP', NULL, 'COMMITTED',
        TIMESTAMP '2026-09-27 09:15:00', TIMESTAMP '2026-09-27 09:15:02', 'seed');

-- ---------------------------------------------------------------------
-- T3: TRANSFER 1000.0000 from Juan Checking -> Maria Checking
-- (2 ledger legs expected: DEBIT Juan Checking, CREDIT Maria Checking)
-- ---------------------------------------------------------------------
INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, currency_code, dest_currency_code, txn_status, initiated_at, completed_at, created_by)
VALUES ('8a2adc48-4cf1-4478-b317-57d60b9bf0ee', 'TRANSFER', 'a86c97cc-1ea5-4767-bd5b-148968354f9c', 'f95e7e52-3259-4b4d-ac66-b186a893cf66',
        1000.0000, 'PHP', 'PHP', 'COMMITTED',
        TIMESTAMP '2026-09-27 10:30:00', TIMESTAMP '2026-09-27 10:30:05', 'seed');

-- ---------------------------------------------------------------------
-- T4: TRANSFER 300.0000 from Maria Wallet -> Juan Savings
-- ---------------------------------------------------------------------
INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, currency_code, dest_currency_code, txn_status, initiated_at, completed_at, created_by)
VALUES ('ffc2ea15-57a3-4d26-9840-611adf6deda6', 'TRANSFER', '86cabb96-61d5-462a-b33b-5a6435649eb4', '3722f77f-3d1b-4d55-8f83-b71bac91d095',
        300.0000, 'PHP', 'PHP', 'COMMITTED',
        TIMESTAMP '2026-09-27 13:45:00', TIMESTAMP '2026-09-27 13:45:04', 'seed');

-- ---------------------------------------------------------------------
-- T5: DEPOSIT +750.0000 into Maria Wallet
-- ---------------------------------------------------------------------
INSERT INTO transaction_master (txn_id, txn_type, debit_account_id, credit_account_id, mutation_amount, currency_code, dest_currency_code, txn_status, initiated_at, completed_at, created_by)
VALUES ('672898a7-a8d9-4041-8574-4fd1c4bc0743', 'DEPOSIT', NULL, '86cabb96-61d5-462a-b33b-5a6435649eb4',
        750.0000, 'PHP', NULL, 'COMMITTED',
        TIMESTAMP '2026-09-27 16:00:00', TIMESTAMP '2026-09-27 16:00:02', 'seed');

-- ---------------------------------------------------------------------
-- Roll the net effect of T1-T5 into account_master.balance_amount so
-- the live balances stay consistent with the transaction history
-- above (recon-service itself never checks this - it only compares
-- transaction_master to ledger_mutation_audit - but a balance that
-- ignores its own transaction history would be wrong on its own terms).
--
--   Juan Savings:   15000.0000 + 2000 (T1) + 300 (T4)  = 17300.0000
--   Juan Checking:   5000.0000 -  500 (T2) - 1000 (T3) =  3500.0000
--   Maria Checking:  8000.0000 + 1000 (T3)             =  9000.0000
--   Maria Wallet:    2500.0000 -  300 (T4) +  750 (T5) =  2950.0000
-- ---------------------------------------------------------------------
UPDATE account_master SET balance_amount = 17300.0000, updated_at = TIMESTAMP '2026-09-27 16:00:02', updated_by = 'seed'
WHERE account_id = '3722f77f-3d1b-4d55-8f83-b71bac91d095';

UPDATE account_master SET balance_amount = 3500.0000, updated_at = TIMESTAMP '2026-09-27 16:00:02', updated_by = 'seed'
WHERE account_id = 'a86c97cc-1ea5-4767-bd5b-148968354f9c';

UPDATE account_master SET balance_amount = 9000.0000, updated_at = TIMESTAMP '2026-09-27 16:00:02', updated_by = 'seed'
WHERE account_id = 'f95e7e52-3259-4b4d-ac66-b186a893cf66';

UPDATE account_master SET balance_amount = 2950.0000, updated_at = TIMESTAMP '2026-09-27 16:00:02', updated_by = 'seed'
WHERE account_id = '86cabb96-61d5-462a-b33b-5a6435649eb4';

COMMIT;

-- ---------------------------------------------------------------------
-- To regenerate a BCrypt hash for a different password/cost factor:
--   In a Spring Boot context: new BCryptPasswordEncoder().encode("yourpassword")
--   Standalone (Python, if `bcrypt` is installed):
--     import bcrypt; bcrypt.hashpw(b"yourpassword", bcrypt.gensalt(rounds=10))
-- Never commit real user passwords/hashes this way outside a seed/demo file.
-- ---------------------------------------------------------------------
