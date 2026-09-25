## Architecture diagram (v4)

```mermaid
%% Core Retail Ledger & Balance Mutation Engine — system architecture (v4)
%% Solid = synchronous REST | Dashed = async Kafka event | Dotted = external HTTP (non-critical-path)

flowchart TD
    Client([Client / Postman / SPA]):::client
    GW["API Gateway<br/>routes + validates JWT"]:::gateway
    FXApi[/"api.frankfurter.dev<br/>external rate source"/]:::external

    subgraph Services["Microservices Layer"]
        direction TB
        Reg["Registration Service<br/>customer + KYC onboarding"]:::service
        Login["Login Service<br/>authenticates, issues JWT"]:::service
        Acct["Accounts Service<br/>new-acct / credit / debit<br/>owns locking + cache<br/>+ consumes fx.conversion.completed"]:::service
        Txn["Transaction Service<br/>deposit / withdrawal / transfer<br/>owns ledger audit trail<br/>+ consumes crosscurrency.settlement.completed"]:::service
        FX["ForEx Service<br/>consumes conversion requests<br/>scheduled rate refresh"]:::fx
        Kafka["Kafka<br/>event broker"]:::broker
        Notif["Notification Service<br/>async alerts<br/>writes NOTIFICATION log"]:::notif
        Recon["Reconciliation Service<br/>periodic consistency check"]:::recon
    end

    subgraph Data["Data Layer"]
        direction TB
        Oracle[("Oracle XE 21c<br/>Master: Customer, Account, Transaction")]:::master
        Redis[("Redis<br/>balance cache + idempotency")]:::cache
        PG[("PostgreSQL 15+<br/>Audit: LEDGER_MUTATION, NOTIFICATION,<br/>FX_CONVERSION, FX_RATE_CACHE")]:::audit
    end

    Client -->|HTTPS| GW
    GW -->|REST| Reg
    GW -->|REST| Login
    GW -->|"REST + JWT"| Acct
    GW -->|"REST + JWT"| Txn

    Reg -->|INSERT customer| Oracle
    Login -->|verify credentials| Oracle

    Txn -->|"validate + read currency (sync)"| Acct
    Txn -->|"same-currency credit/debit (sync)"| Acct
    Acct -->|"SELECT ... FOR UPDATE"| Oracle
    Acct -->|cache balance| Redis
    Txn -->|append LEDGER_MUTATION / TRANSACTION row| PG

    Txn -.->|"publish forex.conversion.requested<br/>(cross-currency only)"| Kafka
    Kafka -.->|consume| FX
    FX -.->|"publish forex.conversion.completed"| Kafka
    Kafka -.->|consume| Acct
    Acct -.->|"publish crosscurrency.settlement.completed"| Kafka
    Kafka -.->|consume| Txn

    FX -->|"read/upsert rate"| PG
    FX -.->|"scheduled poll (hourly, not per-txn)"| FXApi

    Recon -->|"read balances (read-only)"| Oracle
    Recon -->|"read mutations (read-only)"| PG

    Txn -.->|publish transaction.completed| Kafka
    Recon -.->|publish reconciliation.discrepancy| Kafka
    Kafka -.->|consume all events| Notif
    Notif -->|"write NOTIFICATION row (own table)"| PG

    classDef client fill:#e8f0fe,stroke:#4285f4,stroke-width:2px,color:#1a1a1a
    classDef gateway fill:#fff3cd,stroke:#e6a700,stroke-width:2px,color:#1a1a1a
    classDef service fill:#d4edda,stroke:#28a745,stroke-width:2px,color:#1a1a1a
    classDef fx fill:#c9f7dc,stroke:#0f9960,stroke-width:2px,color:#1a1a1a
    classDef notif fill:#e2d9f3,stroke:#6f42c1,stroke-width:2px,color:#1a1a1a
    classDef recon fill:#ffe0b3,stroke:#e67300,stroke-width:2px,color:#1a1a1a
    classDef broker fill:#e2d9f3,stroke:#6f42c1,stroke-width:2px,color:#1a1a1a,stroke-dasharray: 4 2
    classDef master fill:#f8d7da,stroke:#dc3545,stroke-width:2px,color:#1a1a1a
    classDef audit fill:#fde2c8,stroke:#fd7e14,stroke-width:2px,color:#1a1a1a
    classDef cache fill:#cfe2ff,stroke:#0d6efd,stroke-width:2px,color:#1a1a1a
    classDef external fill:#f0f0f0,stroke:#888,stroke-width:1px,color:#1a1a1a,stroke-dasharray: 2 2
```

Notice **Login and Registration have no Kafka edges** — matches last message's decision, and now the diagram visually makes the point for you in a defense: only the genuinely async, critical-path pair (Transaction ↔ ForEx ↔ Accounts) and the always-async Notification/Reconciliation paths touch the broker.

