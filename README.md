# Capstone Banking System

This project is an enterprise-grade multi-service retail banking platform and balance mutation engine built with Java 21, Spring Boot 3, Spring Cloud Gateway, Oracle XE 21c, PostgreSQL 15, Redis, Kafka, and a modern Thymeleaf Web Portal.

The platform provides both:
1. **Interactive Web UI Portal (`http://localhost:8090`)**: Role-aware customer and admin portals built with Thymeleaf and Bootstrap 5.3, complete with KYC onboarding, multi-currency accounts, live ForEx conversion, printable receipts, admin freeze/unfreeze governance, and reconciliation audits.
2. **REST Microservice APIs (`http://localhost:8080`)**: High-throughput ledger engine exposed via Spring Cloud Gateway with JWT stateless security, Redis token blacklisting/caching, row-level pessimistic locking (`SELECT ... FOR UPDATE`), and an immutable PostgreSQL audit trail.

---

## System architecture

| Service              | Port | Purpose                                                     |
| -------------------- | ---: | ----------------------------------------------------------- |
| **frontend-web**     | 8090 | Thymeleaf Web Portal (Customer & Admin Banking UI)          |
| **api-gateway**      | 8080 | Edge gateway, JWT verification, and Redis token blacklisting|
| **registration-service** | 8081 | Customer registration & KYC onboarding                  |
| **login-service**    | 8082 | Authentication and JWT issuance                             |
| **accounts-service** | 8083 | Multi-currency account provisioning & balance lookup        |
| **transaction-service** | 8084 | Pessimistic-locked balance mutation (Deposit/Withdraw/Transfer) |
| **notification-service** | 8085 | Kafka asynchronous notification consumer                 |
| **oracle-db**        | 1521 | Main banking database (Oracle XE 21c - `XEPDB1`)            |
| **postgres-db**      | 5432 | Ledger audit database (PostgreSQL 15 - `ledger_audit`)      |
| **redis**            | 6379 | Token revocation blacklist and balance caching              |
| **kafka**            | 9092 | Event streaming (`customer.registered`, `transaction.events`)|
| **zookeeper**        | 2181 | Kafka cluster coordination                                  |

---

## Prerequisites

Before running this project, make sure you have:

- Java 21 (JDK)
- Maven 3.9+
- Docker Desktop (or Docker Engine)
- Git
- Postman or `curl` (for API testing)

Check versions:

```bash
java -version
mvn -version
docker --version
```

---

## Run from start to finish

### 1) Clone the project

```bash
git clone <your-repo-url>
cd capstone-banking-system
```

### 2) Build all Java modules

The Dockerfiles use lightweight runtime images (`eclipse-temurin:21-jre-alpine`) that copy pre-built JARs from each module's `target/` directory.

Compile and package both the backend services and the frontend web portal from the root:

```bash
# 1. Build all backend microservices
mvn clean package -DskipTests

# 2. Build the frontend web portal
cd frontend-web
mvn clean package -DskipTests
cd ..
```

This generates runnable fat JARs in `*/target/` across all services.

### 3) Start everything with Docker Compose

```bash
docker compose up -d --build
```

This builds every container image and starts the full banking stack in dependency order:
- `zookeeper`, `kafka`, and `redis` start first
- `oracle-db` and `postgres-db` start with container healthchecks
- Application services (`api-gateway`, `registration-service`, `login-service`, `accounts-service`, `transaction-service`, `notification-service`) start once dependencies are healthy
- `frontend-web` starts on port `8090`, pre-configured to communicate internally with `http://api-gateway:8080`

> **Note on First Run**: Oracle XE takes **1–2 minutes** on initial startup to create its datafiles and run the init scripts. Docker Compose healthchecks automatically wait for Oracle and PostgreSQL to report healthy before starting the microservices.

### 4) Database Schema Initialization (and Troubleshooting)

The database schemas are mounted as container init scripts and execute automatically on first volume startup:
- **Oracle XE 21c**: [`sql/oracle/oracle_main_db.sql`](sql/oracle/oracle_main_db.sql) switches to pluggable database `XEPDB1` under schema `LEDGER_APP`, creating `customer_master`, `customer_balance_master` (with `version NUMBER(19)`), `app_user_master`, and `transaction_master`.
- **PostgreSQL 15**: [`sql/postgres/postgres_audit_db.sql`](sql/postgres/postgres_audit_db.sql) initializes the immutable audit tables in database `ledger_audit`.

**If you ever need to manually re-apply the Oracle schema on an existing container:**
```bash
docker exec -i oracle-db bash -c "echo 'exit' | sqlplus -s / as sysdba @/container-entrypoint-initdb.d/oracle_main_db.sql"
docker compose restart registration-service login-service accounts-service transaction-service
```

### 5) Verify all services are running

```bash
docker ps --format "{{.Names}}\t{{.Status}}"
```

Expected output — all 12 containers `Up` with zero unexpected restarts:

```
frontend-web          Up X minutes
api-gateway           Up X minutes
transaction-service   Up X minutes
registration-service  Up X minutes
login-service         Up X minutes
accounts-service      Up X minutes
notification-service  Up X minutes
postgres-db           Up X minutes (healthy)
redis                 Up X minutes (healthy)
oracle-db             Up X minutes (healthy)
kafka                 Up X minutes
zookeeper             Up X minutes
```

Verify service connectivity:

```bash
curl http://localhost:8090/login             # ← Frontend Web Portal (HTTP 200)
curl http://localhost:8080/actuator/health   # ← API Gateway (status: UP)
curl http://localhost:8083/actuator/health   # ← Accounts Service (status: UP)
```

### 6) Watch logs (optional)

To tail all service logs:

```bash
docker compose logs -f
```

To watch a specific microservice:

```bash
docker compose logs -f api-gateway
docker compose logs -f transaction-service
docker compose logs -f frontend-web
docker compose logs -f notification-service
```

### 7) Stop everything

```bash
docker compose down
```

To wipe the database volumes for a completely clean slate (full reset):

```bash
docker compose down -v
```

---

## Web UI Portal (`frontend-web`)

The frontend application provides a full-featured, responsive banking portal accessible at **`http://localhost:8090`**.

### Two Ways to Run the Frontend:

| Mode | Command | When to Use |
| :--- | :--- | :--- |
| **Full-Stack Integrated** | `docker compose up -d --build` | Full end-to-end integration. The frontend talks to `http://api-gateway:8080` and uses live database records. |
| **Zero-Dependency Standalone (Mock)** | `cd frontend-web && mvn spring-boot:run` | Instant local UI testing, defenses, or grading without starting Docker or databases. Pre-seeded with realistic customer and admin personas. |

### Demo Personas (Instant One-Click Testing in Standalone Mode):
When running standalone, use the **Quick Switcher** bar at the top of the screen to jump between roles:
- **Bank Administrator** (`admin@ledgerbank.com` / `admin123`):
  - Executive KPI summary (consolidated liquidity across currencies, customer count, active vs frozen accounts).
  - Customer directory with KYC verification (Approve / Reject identity documents).
  - Account registry (Freeze / Unfreeze accounts with real-time balance mutation locks).
  - Reconciliation monitor (Audit log vs. Master ledger discrepancy inspector).
- **Customer: Juan Dela Cruz** (`juan.delacruz@example.com` / `password123`):
  - Dual-currency portfolio: PHP Savings (₱45,250.00) & USD Checking ($1,500.00).
  - Deposit, withdrawal, account statements, and printable transaction receipts.
- **Customer: Maria Santos** (`maria.santos@example.com` / `password123`):
  - Multi-currency portfolio (PHP, USD, EUR, Digital Wallet).
  - Cross-currency transfer with dynamic real-time ForEx exchange rate calculation.

For full architectural details, Anti-Corruption Layer (ACL) design, and instructions on adapting to backend API contract changes, refer to the [**Frontend Wiring & Contract Guide**](frontend-web/WIRING_AND_CONTRACT_GUIDE.md).

---

## API entry point

All REST calls go through the API Gateway at port `8080`. Never call downstream microservice ports directly.

```
http://localhost:8080
```

| Operation              | Method | Path                                        | Auth |
|------------------------|--------|---------------------------------------------|------|
| Register customer      | POST   | `/api/auth/register`                        | No   |
| Login                  | POST   | `/api/auth/login`                           | No   |
| Logout                 | POST   | `/api/auth/logout`                          | Yes  |
| Create account         | POST   | `/api/accounts`                             | Yes  |
| Get account details    | GET    | `/api/accounts/{accountId}`                 | Yes  |
| Get balance            | GET    | `/api/accounts/{accountId}/balance`         | Yes  |
| Get accounts by customer | GET  | `/api/accounts/customer/{customerId}`       | Yes  |
| Mutate balance         | POST   | `/api/v1/ledger/mutate`                     | Yes  |
| Get audit record       | GET    | `/api/v1/ledger/audit/{txnId}`              | Yes  |

> All `accountId`, `customerId`, and `txnId` values are **UUID strings** — not numbers.

---

## Postman testing flow

Work through each step in order. Values from earlier steps feed into later ones.

---

### Step 1 — Register a customer

**Method:** `POST`
**URL:** `http://localhost:8080/api/auth/register`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "contactNo": "+63-917-555-0100",
  "birthDate": "1995-07-10",
  "password": "Password123!"
}
```

**Expected:** `201 Created`

**Response:**
```json
{
  "success": true,
  "message": "Customer registered successfully",
  "data": {
    "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com"
  }
}
```

Save `data.customerId`. You need it when creating accounts.

Field notes:
- `contactNo` — optional, nullable
- `birthDate` — format `YYYY-MM-DD`, must be in the past
- `password` — minimum 8 characters

---

### Step 2 — Login and get the JWT

**Method:** `POST`
**URL:** `http://localhost:8080/api/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "Password123!"
}
```

**Expected:** `200 OK`

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresInSeconds": 3600,
    "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "email": "john.doe@example.com"
  }
}
```

Copy `data.token`. Add it as `Authorization: Bearer <token>` on every request from here on.

---

### Step 3 — Create a SAVINGS account

**Method:** `POST`
**URL:** `http://localhost:8080/api/accounts`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
```

**Body:**
```json
{
  "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "accountType": "SAVINGS",
  "currencyCode": "PHP"
}
```

**Expected:** `201 Created`

**Response:**
```json
{
  "success": true,
  "message": "Account created",
  "data": {
    "accountId": "11111111-aaaa-bbbb-cccc-222222222222",
    "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "accountType": "SAVINGS",
    "accountStatus": "ACTIVE",
    "balanceAmount": 0,
    "currencyCode": "PHP",
    "createdAt": "2026-09-23T05:00:00"
  }
}
```

Save `data.accountId` as **Account 1**.

Valid `accountType` values: `SAVINGS`, `CHECKING`, `WALLET`
Valid `currencyCode`: any 3-letter ISO code, e.g. `PHP`, `USD`, `EUR`

---

### Step 4 — Create a second account (for transfer testing)

Same as Step 3, change `accountType`:

```json
{
  "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "accountType": "CHECKING",
  "currencyCode": "PHP"
}
```

Save `data.accountId` as **Account 2**.

---

### Step 5 — Check balance

**Method:** `GET`
**URL:** `http://localhost:8080/api/accounts/11111111-aaaa-bbbb-cccc-222222222222/balance`

Replace the UUID with your actual Account 1 `accountId`.

**Headers:**
```
Authorization: Bearer <token>
```

**Expected:** `200 OK` — returns `0` at this point.

---

### Step 6 — Deposit money into Account 1

**Method:** `POST`
**URL:** `http://localhost:8080/api/v1/ledger/mutate`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
```

**Body:**
```json
{
  "accountId": "11111111-aaaa-bbbb-cccc-222222222222",
  "counterpartyAccountId": null,
  "txnType": "DEPOSIT",
  "amount": 5000.00,
  "idempotencyKey": "deposit-acct1-5000-001"
}
```

**Expected:** `201 Created`

**Response:**
```json
{
  "success": true,
  "message": "DEPOSIT processed",
  "data": {
    "txnId": "ffffffff-0000-1111-2222-333333333333",
    "accountId": "11111111-aaaa-bbbb-cccc-222222222222",
    "txnType": "DEPOSIT",
    "amount": 5000.00,
    "balanceAfter": 5000.00,
    "txnStatus": "COMMITTED",
    "timestamp": "2026-09-23T05:10:00Z"
  }
}
```

Save `data.txnId` to check the audit record in Step 9.

---

### Step 7 — Withdraw money from Account 1

**Method:** `POST`
**URL:** `http://localhost:8080/api/v1/ledger/mutate`

**Body:**
```json
{
  "accountId": "11111111-aaaa-bbbb-cccc-222222222222",
  "counterpartyAccountId": null,
  "txnType": "WITHDRAWAL",
  "amount": 500.00,
  "idempotencyKey": "withdrawal-acct1-500-001"
}
```

**Expected:** `balanceAfter` = `4500.00`

To confirm the non-negative balance guard works, try withdrawing more than the current balance — you should get a `409 Conflict` error.

---

### Step 8 — Transfer from Account 1 to Account 2

**Method:** `POST`
**URL:** `http://localhost:8080/api/v1/ledger/mutate`

**Body:**
```json
{
  "accountId": "11111111-aaaa-bbbb-cccc-222222222222",
  "counterpartyAccountId": "22222222-bbbb-cccc-dddd-333333333333",
  "txnType": "TRANSFER",
  "amount": 1500.50,
  "idempotencyKey": "transfer-acct1-acct2-1500-001"
}
```

Replace both UUIDs with your actual Account 1 and Account 2 IDs.

**Expected:**
- Account 1 balance decreases by 1500.50
- Account 2 balance increases by 1500.50
- `txnStatus: "COMMITTED"`

Constraints the service enforces:
- `accountId` and `counterpartyAccountId` must differ — same value on both returns `400`
- `counterpartyAccountId` is required for TRANSFER — omitting it returns `400`
- Source must have sufficient funds — returns `409` on shortfall

---

### Step 9 — Fetch the ledger audit record

**Method:** `GET`
**URL:** `http://localhost:8080/api/v1/ledger/audit/ffffffff-0000-1111-2222-333333333333`

Replace the UUID with `txnId` from any transaction response.

**Headers:**
```
Authorization: Bearer <token>
```

**What to expect per operation:**

| txnType    | Rows returned | mutationType values         |
|------------|---------------|-----------------------------|
| DEPOSIT    | 1             | `CREDIT`                    |
| WITHDRAWAL | 1             | `DEBIT`                     |
| TRANSFER   | 2             | `DEBIT` (source) + `CREDIT` (dest) |

All rows will have `auditState: "COMMITTED"` and `txnType` matching the original operation.

---

### Step 10 — Logout

**Method:** `POST`
**URL:** `http://localhost:8080/api/auth/logout`

**Headers:**
```
Authorization: Bearer <token>
```

**Expected:** `200 OK`

The token is now blacklisted in Redis. Any subsequent request using it will be rejected with `401 Unauthorized` by the gateway.

---

## Complete end-to-end sequence

| # | Action | Value to save |
|---|--------|---------------|
| 1 | Register → `POST /api/auth/register` | `customerId` |
| 2 | Login → `POST /api/auth/login` | `token` |
| 3 | Create Account 1 (SAVINGS/PHP) → `POST /api/accounts` | `accountId` (acct1) |
| 4 | Create Account 2 (CHECKING/PHP) → `POST /api/accounts` | `accountId` (acct2) |
| 5 | Deposit 5000 → `POST /api/v1/ledger/mutate` | `txnId` |
| 6 | Withdraw 500 → `POST /api/v1/ledger/mutate` | `txnId` |
| 7 | Transfer 1500.50 acct1 → acct2 → `POST /api/v1/ledger/mutate` | `txnId` |
| 8 | Check acct1 balance → `GET /api/accounts/{acct1}/balance` | expect `3000.00` |
| 9 | Check acct2 balance → `GET /api/accounts/{acct2}/balance` | expect `1500.50` |
| 10 | Audit deposit txn → `GET /api/v1/ledger/audit/{txnId}` | 1 CREDIT row |
| 11 | Audit transfer txn → `GET /api/v1/ledger/audit/{txnId}` | 1 DEBIT + 1 CREDIT row |
| 12 | Logout → `POST /api/auth/logout` | token blacklisted |

---

## Idempotency key rules

Every mutation request (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`) requires a unique `idempotencyKey`:

- Submitting the same key twice returns the **cached original response** — the mutation is not re-executed
- Keys expire after 24 hours
- Use a pattern that makes each operation unique:

```
deposit-<accountId-prefix>-<amount>-<sequence>
withdrawal-<accountId-prefix>-<amount>-<sequence>
transfer-<src-prefix>-<dst-prefix>-<amount>-<sequence>
```

Example:
```
deposit-11111111-5000-001
withdrawal-11111111-500-001
transfer-11111111-22222222-1500-001
```

---

## Error cases worth testing

| Scenario | Expected |
|----------|----------|
| Withdraw more than balance | `409 Conflict` — InsufficientBalanceException |
| Transfer to the same account | `400 Bad Request` |
| TRANSFER without `counterpartyAccountId` | `400 Bad Request` |
| Duplicate idempotency key in-flight | `409 Conflict` — IdempotencyConflictException |
| Expired or blacklisted token | `401 Unauthorized` from gateway |
| Invalid `txnType` value | `400 Bad Request` |
| Zero or negative `amount` | `400 Bad Request` |
| Missing required field | `400 Bad Request` with field-level validation message |

---

## Mock payloads (copy-paste ready)

### Register
```json
{
  "firstName": "Alice",
  "lastName": "Reyes",
  "email": "alice.reyes@example.com",
  "contactNo": "+63-917-555-0100",
  "birthDate": "1990-04-12",
  "password": "StrongPass123!"
}
```

### Login
```json
{
  "email": "alice.reyes@example.com",
  "password": "StrongPass123!"
}
```

### Create account
```json
{
  "customerId": "<customerId from register>",
  "accountType": "SAVINGS",
  "currencyCode": "PHP"
}
```

### Deposit
```json
{
  "accountId": "<accountId>",
  "counterpartyAccountId": null,
  "txnType": "DEPOSIT",
  "amount": 10000.00,
  "idempotencyKey": "deposit-acct1-10000-001"
}
```

### Withdrawal
```json
{
  "accountId": "<accountId>",
  "counterpartyAccountId": null,
  "txnType": "WITHDRAWAL",
  "amount": 2500.00,
  "idempotencyKey": "withdrawal-acct1-2500-001"
}
```

### Transfer
```json
{
  "accountId": "<source-accountId>",
  "counterpartyAccountId": "<dest-accountId>",
  "txnType": "TRANSFER",
  "amount": 1000.00,
  "idempotencyKey": "transfer-acct1-acct2-1000-001"
}
```

---

## Notes for testing

- All IDs in request bodies and URL paths are **UUID strings**, not numbers.
- The `Authorization` header must be exactly `Bearer <token>` with no extra quotes.
- Always use a new unique `idempotencyKey` per logical transaction unless you intentionally want to test idempotency replay.
- `txnId` in the mutation response is the key to retrieve ledger audit rows.
- Notifications are consumed asynchronously — check the logs to see them:

```bash
docker compose logs -f notification-service
```

- To watch all infrastructure logs at once:

```bash
docker compose logs -f oracle-db postgres-db redis kafka
```

---

## Expected results when everything works

| Verification | What to confirm |
|--------------|-----------------|
| Registration | `201 Created`, `customerId` UUID in response |
| Login | `200 OK`, JWT in `data.token`, `customerId` matches |
| Account creation | `201 Created`, `accountId` UUID, `balanceAmount: 0`, `accountStatus: "ACTIVE"` |
| Deposit | `txnStatus: "COMMITTED"`, `balanceAfter` equals deposited amount |
| Withdrawal | `balanceAfter` correctly decremented |
| Insufficient balance | `409 Conflict` error, balance unchanged |
| Transfer | Source decremented, destination incremented, both by exact `amount` |
| Audit — DEPOSIT | 1 row, `mutationType: "CREDIT"`, `auditState: "COMMITTED"` |
| Audit — WITHDRAWAL | 1 row, `mutationType: "DEBIT"`, `auditState: "COMMITTED"` |
| Audit — TRANSFER | 2 rows: `"DEBIT"` for source + `"CREDIT"` for destination |
| Logout | `200 OK`, subsequent call with same token returns `401` |
