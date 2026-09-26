```mermaid
erDiagram
    %% ===== ORACLE XE 21c - MASTER DB =====
    CUSTOMER_MASTER ||--o{ ACCOUNT_MASTER : owns
    CUSTOMER_MASTER ||--o{ APP_USER_MASTER : has
    ACCOUNT_MASTER ||--o{ TRANSACTION_MASTER : "debit leg"
    ACCOUNT_MASTER ||--o{ TRANSACTION_MASTER : "credit leg"
    TRANSACTION_MASTER ||--o{ OUTBOX_MAIN : "raises event"

    %% ===== cross-database (logical, not enforced FKs) =====
    CUSTOMER_MASTER ||--o{ NOTIFICATION_AUDIT : "has history in"
    ACCOUNT_MASTER ||--o{ LEDGER_MUTATION_AUDIT : "has history in"
    TRANSACTION_MASTER ||--o{ LEDGER_MUTATION_AUDIT : "has history in"
    TRANSACTION_MASTER ||--o| FX_CONVERSION_AUDIT : "converted via"

    %% ===== POSTGRESQL 15+ - AUDIT DB (incl. ForEx Service tables) =====
    RECON_RUN_AUDIT ||--o{ RECON_RESULT_AUDIT : contains
    LEDGER_MUTATION_AUDIT ||--o| RECON_RESULT_AUDIT : "matched by"
    LEDGER_MUTATION_AUDIT ||--o| RECON_RESULT_AUDIT : "flagged as duplicate of"
    LEDGER_MUTATION_AUDIT ||--o{ OUTBOX_AUDIT : "raises event"
    RECON_RESULT_AUDIT ||--o{ OUTBOX_AUDIT : "raises event"

    CUSTOMER_MASTER {
        string customer_id PK
        string first_name
        string last_name
        string email
        string contact_no
        date birth_date
        datetime created_at
        string created_by
        datetime updated_at
        string updated_by
    }

    ACCOUNT_MASTER {
        string account_id PK
        string customer_id FK
        string account_type "SAVINGS, CHECKING, WALLET"
        string currency_code
        string account_status "ACTIVE, FROZEN, CLOSED"
        decimal balance_amount "NUMBER(18,4), >= 0"
        datetime created_at
        string created_by
        datetime updated_at
        string updated_by
    }

    APP_USER_MASTER {
        string user_id PK
        string customer_id FK
        string username
        string password_hash
        string active_status "ACTIVE, SUSPENDED, LOCKED, DISABLED"
        string role "CUSTOMER, ADMIN"
        datetime created_at
        string created_by
        datetime updated_at
        string updated_by
    }

    TRANSACTION_MASTER {
        string txn_id PK
        string txn_type "WITHDRAWAL, DEPOSIT, TRANSFER"
        string debit_account_id FK "nullable - required for WITHDRAWAL/TRANSFER"
        string credit_account_id FK "nullable - required for DEPOSIT/TRANSFER"
        decimal mutation_amount "NUMBER(18,4), > 0 - source-side amount"
        string is_cross_currency "Y/N"
        decimal fx_rate "NUMBER(18,8), nullable - null for same-currency txns"
        decimal dest_amount "NUMBER(18,4), nullable - converted amount"
        string txn_status "PENDING, COMMITTED, ROLLED_BACK"
        datetime initiated_at
        datetime completed_at "nullable while pending"
        datetime created_at
        string created_by
        datetime updated_at
        string updated_by
    }

    OUTBOX_MAIN {
        string outbox_id PK "app-generated VARCHAR2(36)"
        string aggregate_type "e.g. TRANSACTION"
        string aggregate_id "txn_id"
        string event_type "e.g. transaction.completed"
        json payload
        string status "PENDING, PUBLISHED, FAILED"
        datetime created_at
        datetime published_at "nullable"
    }

    NOTIFICATION_AUDIT {
        string notif_id PK
        string customer_id
        string message
        string status "PENDING, SENT, FAILED"
        datetime created_at
    }

    LEDGER_MUTATION_AUDIT {
        string mutation_uuid PK
        string txn_id
        string account_id
        decimal mutation_amount "NUMERIC(18,4), always positive"
        string mutation_type "DEBIT, CREDIT"
        string txn_type "WITHDRAWAL, DEPOSIT, TRANSFER"
        string audit_state "PENDING, COMMITTED, ROLLED_BACK"
        datetime created_at
    }

    RECON_RUN_AUDIT {
        string run_id PK
        datetime run_started_at
        datetime run_completed_at
        datetime window_start
        datetime window_end
        int total_txn_checked
        int total_matched
        int total_exceptions
        string run_status "RUNNING, COMPLETED, FAILED"
    }

    RECON_RESULT_AUDIT {
        string result_id PK
        string run_id FK
        string txn_id
        string account_id
        string mutation_uuid FK "nullable"
        string duplicate_mutation_uuid FK "nullable"
        string recon_status "MATCHED, EXCEPTION"
        string exception_type "nullable"
        decimal expected_amount
        decimal actual_amount
        decimal variance_amount "generated: actual - expected"
        string txn_status
        string ledger_audit_state
        datetime txn_completed_at
        datetime ledger_posted_at
        int posting_lag_seconds
        string severity "LOW, MEDIUM, HIGH, CRITICAL"
        datetime created_at
    }

    FX_CONVERSION_AUDIT {
        string conversion_id PK
        string txn_id
        string source_currency
        string dest_currency
        decimal source_amount "NUMERIC(18,4)"
        decimal fx_rate "NUMERIC(18,8)"
        decimal dest_amount "NUMERIC(18,4)"
        string conversion_status "COMPLETED, FAILED"
        datetime created_at
    }

    FX_RATE_CACHE {
        string rate_id PK
        string base_currency
        string quote_currency
        decimal rate "NUMERIC(18,8), > 0"
        datetime fetched_at
        string source "default frankfurter.dev"
    }

    OUTBOX_AUDIT {
        string outbox_id PK
        string aggregate_type "e.g. LEDGER_MUTATION, RECON_RESULT"
        string aggregate_id "txn_id or mutation_uuid"
        string event_type "e.g. ledger.mutation.posted"
        json payload
        string status "PENDING, PUBLISHED, FAILED"
        datetime created_at
        datetime published_at "nullable"
    }

    %% ===== ownership (by owning/writing service) =====
    classDef ledgerSvc   fill:#cfe2ff,stroke:#0d6efd,color:#000
    classDef forexSvc    fill:#d1e7dd,stroke:#198754,color:#000
    classDef reconSvc    fill:#fff3cd,stroke:#997404,color:#000
    classDef notifSvc    fill:#f8d7da,stroke:#b02a37,color:#000
    classDef sharedSvc   fill:#e2d9f3,stroke:#6f42c1,color:#000

    class CUSTOMER_MASTER ledgerSvc
    class ACCOUNT_MASTER ledgerSvc
    class APP_USER_MASTER ledgerSvc
    class TRANSACTION_MASTER ledgerSvc
    class OUTBOX_MAIN ledgerSvc
    class LEDGER_MUTATION_AUDIT ledgerSvc

    class FX_CONVERSION_AUDIT forexSvc
    class FX_RATE_CACHE forexSvc

    class RECON_RUN_AUDIT reconSvc
    class RECON_RESULT_AUDIT reconSvc

    class NOTIFICATION_AUDIT notifSvc

    class OUTBOX_AUDIT sharedSvc

```