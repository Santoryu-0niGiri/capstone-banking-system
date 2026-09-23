# Capstone Banking System

This project is a multi-service banking platform built with Java 21, Spring Boot 3, Spring Cloud Gateway, Oracle, PostgreSQL, Redis, and Kafka.

The app is designed to let you:

- register a customer
- log in and receive a JWT
- create accounts for a customer
- check account balance
- debit, credit, and transfer money
- test the transaction audit flow through Postman

---

## System architecture

| Service              | Port | Purpose                              |
| -------------------- | ---: | ------------------------------------ |
| api-gateway          | 8080 | API entry point and JWT validation   |
| registration-service | 8081 | Customer registration                |
| login-service        | 8082 | Login and JWT issuance               |
| accounts-service     | 8083 | Account creation and balance lookup  |
| transaction-service  | 8084 | Debit / credit / transfer processing |
| notification-service | 8085 | Kafka notification consumer          |
| oracle-db            | 1521 | Main banking database                |
| postgres-db          | 5432 | Ledger audit database                |
| redis                | 6379 | Cache and token/session support      |
| kafka                | 9092 | Event streaming                      |

---

## Prerequisites

Before running this project, make sure you have:

- Java 21
- Maven
- Docker Desktop (or Docker Engine)
- Postman
- Git

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

### 2) Start the infrastructure services

This starts Oracle, PostgreSQL, Redis, Kafka, and Zookeeper:

```bash
docker compose up -d oracle-db postgres-db redis zookeeper kafka
```

Wait until the database containers are healthy before continuing. Oracle can take a few minutes on first start.

### 3) Build the Java services

From the root folder:

```bash
mvn clean package -DskipTests
```

This builds all modules in the multi-module Maven project.

### 4) Start the application services

```bash
docker compose up --build
```

This starts all service containers and the gateway. The gateway is the main entry point for testing.

### 5) Verify the services are running

Open these URLs in your browser or use Postman:

```text
http://localhost:8080/actuator/health
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
http://localhost:8084/actuator/health
```

If the app is running correctly, the gateway should be available on port 8080.

### 6) Stop everything later

```bash
docker compose down -v
```

---

## API entry point

All tests should go through the gateway, not directly to each service:

```text
http://localhost:8080
```

Gateway routes:

- Registration: `http://localhost:8080/api/auth/register`
- Login: `http://localhost:8080/api/auth/login`
- Logout: `http://localhost:8080/api/auth/logout`
- Accounts: `http://localhost:8080/api/accounts`
- Transactions: `http://localhost:8080/api/transactions`

Important: account and transaction endpoints require a valid JWT in the Authorization header.

---

## Postman testing flow

### Step 1: Register a customer

Method: POST

URL:

```text
http://localhost:8080/api/auth/register
```

Headers:

```http
Content-Type: application/json
```

Body JSON:

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phoneNumber": "+1-555-0133",
  "birthday": "1995-07-10",
  "password": "Password123!"
}
```

Expected result: status 201 Created.

The response contains a customer id, which you will use for account creation.

---

### Step 2: Login and get JWT token

Method: POST

URL:

```text
http://localhost:8080/api/auth/login
```

Headers:

```http
Content-Type: application/json
```

Body JSON:

```json
{
  "email": "john.doe@example.com",
  "password": "Password123!"
}
```

Expected response:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9....",
    "tokenType": "Bearer",
    "expiresInSeconds": 3600,
    "custId": 1,
    "email": "john.doe@example.com"
  },
  "timestamp": "2026-09-22T10:00:00Z"
}
```

Copy the token value from `data.token`.

---

### Step 3: Create an account

Method: POST

URL:

```text
http://localhost:8080/api/accounts
```

Headers:

```http
Content-Type: application/json
Authorization: Bearer <PASTE_TOKEN_HERE>
```

Body JSON:

```json
{
  "custId": 1,
  "acctType": "SAVINGS"
}
```

You can also create a second account for transfer testing:

```json
{
  "custId": 1,
  "acctType": "CHECKING"
}
```

Expected result: status 201 Created.

Save the returned account number (`acctNo`) because you will use it for balance checks and transactions.

---

### Step 4: Check balance

Method: GET

URL:

```text
http://localhost:8080/api/accounts/100001/balance
```

Headers:

```http
Authorization: Bearer <PASTE_TOKEN_HERE>
```

Replace `100001` with the actual account number returned earlier.

---

### Step 5: Debit money from an account

Method: POST

URL:

```text
http://localhost:8080/api/transactions/debit
```

Headers:

```http
Content-Type: application/json
Authorization: Bearer <PASTE_TOKEN_HERE>
```

Body JSON:

```json
{
  "acctNo": 100001,
  "counterpartyAcctNo": null,
  "amount": 250.0,
  "idempotencyKey": "debit-100001-250-001"
}
```

Expected: new balance reduced by 250.00.

---

### Step 6: Credit money to an account

Method: POST

URL:

```text
http://localhost:8080/api/transactions/credit
```

Headers:

```http
Content-Type: application/json
Authorization: Bearer <PASTE_TOKEN_HERE>
```

Body JSON:

```json
{
  "acctNo": 100001,
  "counterpartyAcctNo": null,
  "amount": 1000.0,
  "idempotencyKey": "credit-100001-1000-001"
}
```

---

### Step 7: Transfer money between two accounts

Method: POST

URL:

```text
http://localhost:8080/api/transactions/transfer
```

Headers:

```http
Content-Type: application/json
Authorization: Bearer <PASTE_TOKEN_HERE>
```

Body JSON:

```json
{
  "acctNo": 100001,
  "counterpartyAcctNo": 100002,
  "amount": 150.5,
  "idempotencyKey": "transfer-100001-100002-001"
}
```

This performs a transfer from account 100001 to 100002.

Important:

- source and destination accounts must be different
- source account must have enough balance
- idempotencyKey must be unique per logical operation

---

### Step 8: Fetch transaction audit record

Method: GET

URL:

```text
http://localhost:8080/api/transactions/<txnId>
```

Headers:

```http
Authorization: Bearer <PASTE_TOKEN_HERE>
```

The txnId is returned in the transaction response body from debit, credit, or transfer operations.

---

## Example end-to-end flow

This is a realistic sequence to test in Postman:

1. Register customer
2. Login
3. Create account 1
4. Create account 2
5. Credit account 1 with 1000
6. Transfer 150.50 from account 1 to account 2
7. Debit account 2 with 75.00
8. Check balances
9. Query transaction details by txnId

---

## Useful mock payloads

### Registration

```json
{
  "firstName": "Alice",
  "lastName": "Nguyen",
  "email": "alice.nguyen@example.com",
  "phoneNumber": "+1-415-555-0100",
  "birthday": "1990-04-12",
  "password": "StrongPass123!"
}
```

### Login

```json
{
  "email": "alice.nguyen@example.com",
  "password": "StrongPass123!"
}
```

### Create account

```json
{
  "custId": 1,
  "acctType": "SAVINGS"
}
```

### Debit

```json
{
  "acctNo": 100001,
  "counterpartyAcctNo": null,
  "amount": 250.0,
  "idempotencyKey": "debit-100001-250-001"
}
```

### Credit

```json
{
  "acctNo": 100001,
  "counterpartyAcctNo": null,
  "amount": 500.0,
  "idempotencyKey": "credit-100001-500-001"
}
```

### Transfer

```json
{
  "acctNo": 100001,
  "counterpartyAcctNo": 100002,
  "amount": 150.5,
  "idempotencyKey": "transfer-100001-100002-001"
}
```

---

## Notes for testing

- Use the gateway URL on port 8080 for all external testing.
- Authorization is required for account and transaction calls.
- Use a new unique idempotency key each time, otherwise the service can reject duplicate requests.
- Transaction responses include txnId, which is used to fetch the ledger audit history.
- If the app fails at startup, check Docker logs and confirm the database containers are healthy first.

```bash
docker compose logs -f oracle-db postgres-db redis kafka api-gateway
```

---

## Expected results when everything works

If the app is working properly, you should be able to:

- register a customer successfully
- log in and receive a JWT
- create at least one account
- check balance values
- make debit, credit, and transfer requests
- receive transaction audit records and a successful response payload

This confirms the banking flow is functioning from the client side through the gateway, services, databases, and messaging layer.
