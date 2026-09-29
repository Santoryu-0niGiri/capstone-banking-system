-- =====================================================================
-- POSTGRESQL 15+ - SEED DATA
-- 02_seed_data.sql - runs after 01_postgres_audit_db.sql (alphabetical
-- init-script order), so rename your existing schema file to
-- 01_postgres_audit_db.sql if it isn't already.
--
-- ledger_mutation_audit rows here use the SAME txn_id and the SAME
-- mutation_amount as the transaction_master rows in
-- sql/oracle/03_seed_transactions.sql, so a reconciliation-service run
-- over 2026-09-27 comes back 100% MATCHED, zero exceptions:
--
--   T1 DEPOSIT   +2000.0000  Juan Savings   -> 1 CREDIT leg
--   T2 WITHDRAWAL -500.0000  Juan Checking  -> 1 DEBIT leg
--   T3 TRANSFER  1000.0000   Juan Chk->Maria Chk -> 1 DEBIT + 1 CREDIT leg
--   T4 TRANSFER   300.0000   Maria Wal->Juan Sav -> 1 DEBIT + 1 CREDIT leg
--   T5 DEPOSIT   +750.0000   Maria Wallet   -> 1 CREDIT leg
--
-- (7 ledger_mutation_audit rows total for 5 transactions.)
-- =====================================================================

-- ---------------------------------------------------------------------
-- LEDGER_MUTATION_AUDIT
-- Every audit_state below is 'COMMITTED', matching each transaction's
-- txn_status in Oracle exactly - that shared vocabulary is what a
-- STATUS_MISMATCH check relies on. created_at is set a few seconds
-- after each transaction's completed_at (posting lag), safely under
-- the default 300s LATE_POSTING threshold.
-- ---------------------------------------------------------------------

-- T1: DEPOSIT into Juan Savings - 1 CREDIT leg
INSERT INTO ledger_mutation_audit (mutation_uuid, txn_id, account_id, currency_code, mutation_amount, mutation_type, txn_type, audit_state, created_at)
VALUES ('7b006281-4871-4dc6-b17f-04aafcd2810a', '05da5006-2cc5-4dd0-87ab-aeca1f0f7ab1',
        '3722f77f-3d1b-4d55-8f83-b71bac91d095', 'PHP', 2000.0000, 'CREDIT', 'DEPOSIT', 'COMMITTED',
        TIMESTAMPTZ '2026-09-27 08:00:05+00');

-- T2: WITHDRAWAL from Juan Checking - 1 DEBIT leg
INSERT INTO ledger_mutation_audit (mutation_uuid, txn_id, account_id, currency_code, mutation_amount, mutation_type, txn_type, audit_state, created_at)
VALUES ('ad4a2ffd-df4c-46a8-baee-84652f9c3672', 'b314f899-2fc0-4087-80e6-09e87ece0d49',
        'a86c97cc-1ea5-4767-bd5b-148968354f9c', 'PHP', 500.0000, 'DEBIT', 'WITHDRAWAL', 'COMMITTED',
        TIMESTAMPTZ '2026-09-27 09:15:04+00');

-- T3: TRANSFER Juan Checking -> Maria Checking - DEBIT + CREDIT legs
INSERT INTO ledger_mutation_audit (mutation_uuid, txn_id, account_id, currency_code, mutation_amount, mutation_type, txn_type, audit_state, created_at)
VALUES ('0c221483-efea-45be-adfc-4576500866d4', '8a2adc48-4cf1-4478-b317-57d60b9bf0ee',
        'a86c97cc-1ea5-4767-bd5b-148968354f9c', 'PHP', 1000.0000, 'DEBIT', 'TRANSFER', 'COMMITTED',
        TIMESTAMPTZ '2026-09-27 10:30:06+00');
INSERT INTO ledger_mutation_audit (mutation_uuid, txn_id, account_id, currency_code, mutation_amount, mutation_type, txn_type, audit_state, created_at)
VALUES ('098cfaa7-06b1-4a07-bf5e-80d19bd30ed0', '8a2adc48-4cf1-4478-b317-57d60b9bf0ee',
        'f95e7e52-3259-4b4d-ac66-b186a893cf66', 'PHP', 1000.0000, 'CREDIT', 'TRANSFER', 'COMMITTED',
        TIMESTAMPTZ '2026-09-27 10:30:07+00');

-- T4: TRANSFER Maria Wallet -> Juan Savings - DEBIT + CREDIT legs
INSERT INTO ledger_mutation_audit (mutation_uuid, txn_id, account_id, currency_code, mutation_amount, mutation_type, txn_type, audit_state, created_at)
VALUES ('de025c6c-547a-4010-980d-d07abc036d30', 'ffc2ea15-57a3-4d26-9840-611adf6deda6',
        '86cabb96-61d5-462a-b33b-5a6435649eb4', 'PHP', 300.0000, 'DEBIT', 'TRANSFER', 'COMMITTED',
        TIMESTAMPTZ '2026-09-27 13:45:05+00');
INSERT INTO ledger_mutation_audit (mutation_uuid, txn_id, account_id, currency_code, mutation_amount, mutation_type, txn_type, audit_state, created_at)
VALUES ('bf6978e7-c5c1-4060-bf2a-12862b82d44c', 'ffc2ea15-57a3-4d26-9840-611adf6deda6',
        '3722f77f-3d1b-4d55-8f83-b71bac91d095', 'PHP', 300.0000, 'CREDIT', 'TRANSFER', 'COMMITTED',
        TIMESTAMPTZ '2026-09-27 13:45:06+00');

-- T5: DEPOSIT into Maria Wallet - 1 CREDIT leg
INSERT INTO ledger_mutation_audit (mutation_uuid, txn_id, account_id, currency_code, mutation_amount, mutation_type, txn_type, audit_state, created_at)
VALUES ('f6c7d7e6-0fda-4ec8-8804-03ced5dba3fa', '672898a7-a8d9-4041-8574-4fd1c4bc0743',
        '86cabb96-61d5-462a-b33b-5a6435649eb4', 'PHP', 750.0000, 'CREDIT', 'DEPOSIT', 'COMMITTED',
        TIMESTAMPTZ '2026-09-27 16:00:03+00');

-- ---------------------------------------------------------------------
-- OUTBOX_AUDIT
-- One 'transaction.completed' event per transaction, already marked
-- PUBLISHED, as if Transaction Service's own outbox relay had already
-- delivered them - so you're not left with a pile of stale PENDING
-- rows that reconciliation-service's OutboxRelay has nothing to do
-- with (those PENDING rows will instead be the ones recon itself
-- writes when you trigger a run).
-- ---------------------------------------------------------------------
INSERT INTO outbox_audit (outbox_id, aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at)
VALUES ('b2e9bcdf-2e6a-4f2b-b9c9-2fc2b0483214', 'TRANSACTION', '05da5006-2cc5-4dd0-87ab-aeca1f0f7ab1',
        'transaction.completed', '{"txnId":"05da5006-2cc5-4dd0-87ab-aeca1f0f7ab1","txnType":"DEPOSIT","amount":2000.0000}',
        'PUBLISHED', TIMESTAMPTZ '2026-09-27 08:00:03+00', TIMESTAMPTZ '2026-09-27 08:00:05+00');

INSERT INTO outbox_audit (outbox_id, aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at)
VALUES ('980cfdf2-5b51-44e9-a16c-0b1ed784ea87', 'TRANSACTION', 'b314f899-2fc0-4087-80e6-09e87ece0d49',
        'transaction.completed', '{"txnId":"b314f899-2fc0-4087-80e6-09e87ece0d49","txnType":"WITHDRAWAL","amount":500.0000}',
        'PUBLISHED', TIMESTAMPTZ '2026-09-27 09:15:02+00', TIMESTAMPTZ '2026-09-27 09:15:04+00');

INSERT INTO outbox_audit (outbox_id, aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at)
VALUES ('7aec3749-93a3-420b-a042-0bc990cb10c9', 'TRANSACTION', '8a2adc48-4cf1-4478-b317-57d60b9bf0ee',
        'transaction.completed', '{"txnId":"8a2adc48-4cf1-4478-b317-57d60b9bf0ee","txnType":"TRANSFER","amount":1000.0000}',
        'PUBLISHED', TIMESTAMPTZ '2026-09-27 10:30:05+00', TIMESTAMPTZ '2026-09-27 10:30:07+00');

INSERT INTO outbox_audit (outbox_id, aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at)
VALUES ('793778b6-565a-41e7-918c-1af00724a3b1', 'TRANSACTION', 'ffc2ea15-57a3-4d26-9840-611adf6deda6',
        'transaction.completed', '{"txnId":"ffc2ea15-57a3-4d26-9840-611adf6deda6","txnType":"TRANSFER","amount":300.0000}',
        'PUBLISHED', TIMESTAMPTZ '2026-09-27 13:45:04+00', TIMESTAMPTZ '2026-09-27 13:45:06+00');

INSERT INTO outbox_audit (outbox_id, aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at)
VALUES ('7206bef3-a1a9-409c-9e6e-fe6360139eb4', 'TRANSACTION', '672898a7-a8d9-4041-8574-4fd1c4bc0743',
        'transaction.completed', '{"txnId":"672898a7-a8d9-4041-8574-4fd1c4bc0743","txnType":"DEPOSIT","amount":750.0000}',
        'PUBLISHED', TIMESTAMPTZ '2026-09-27 16:00:02+00', TIMESTAMPTZ '2026-09-27 16:00:03+00');
