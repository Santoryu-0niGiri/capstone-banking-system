# Capstone Banking System - Test Case Verification Commands

This document provides the exact, copy-pasteable terminal commands to manually run and verify every test case listed in `capstone_test_cases.csv` and `test_results_report.csv`.

---

## Step 0: Setup Environment Variables (Run Once First)

Open PowerShell in the project directory and run this block once. It logs in, saves your JWT Bearer token into `$TOKEN`, and fetches your two test accounts:

```powershell
# Authenticate and get JWT Token
$login = '{"email":"alice.reyes@example.com","password":"StrongPass123!"}' | curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" --data-binary '@-' | ConvertFrom-Json
$TOKEN = $login.data.token
$CUSTOMER_ID = $login.data.customerId

# Fetch Account 1 & Account 2
$accts = curl.exe -s http://localhost:8080/api/accounts/customer/$CUSTOMER_ID -H "Authorization: Bearer $TOKEN" | ConvertFrom-Json
$ACCT1 = $accts.data[0].accountId
$ACCT2 = $accts.data[1].accountId

Write-Host "Setup Completed!" -ForegroundColor Green
Write-Host "Token: $TOKEN" -ForegroundColor Cyan
Write-Host "Account 1: $ACCT1 | Account 2: $ACCT2" -ForegroundColor Yellow
```

---

## Suite 1: System Infrastructure & Concurrency

### [TC-SYS-01] Core Containers Up & Running
* **Objective**: Verify that all 12 microservices, Oracle, PostgreSQL, Redis, and Kafka are running and healthy.
```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```
* **Expected Output**: All containers report `Up`, and `oracle-db`, `postgres-db`, and `redis` report `(healthy)`.

---

### [TC-CON-01] Parallel Same-Account Withdrawals (Race Condition Guard)
* **Objective**: Verify that 10 simultaneous debit requests against a single account serialize via row locks (`SELECT ... FOR UPDATE`), preventing lost updates and overdrafts.
```powershell
mvn test -pl transaction-service -Dtest=ParallelWithdrawalConcurrencyTest
```
* **Expected Output**:
  ```text
  [PASSED] CONCURRENCY LOCK TEST PASSED!
  -> Actual Final Balance: $0.00 (Zero lost updates, no negative balance)
  ```

---

### [TC-CON-02] Bi-directional Simultaneous Transfers (Deadlock Elimination)
* **Objective**: Verify that simultaneous cross-transfers ($A \rightarrow B$ and $B \rightarrow A$) do not trigger `ORA-00060` deadlocks.
```powershell
1..10 | ForEach-Object {
    $src = if ($_ % 2 -eq 0) { $ACCT1 } else { $ACCT2 }
    $dst = if ($_ % 2 -eq 0) { $ACCT2 } else { $ACCT1 }
    Start-Job -ScriptBlock {
        param($s, $d, $t)
        $body = "{`"accountId`":`"$s`",`"counterpartyAccountId`":`"$d`",`"txnType`":`"TRANSFER`",`"amount`":5.00,`"idempotencyKey`":`"dl-$([guid]::NewGuid())`"}"
        $body | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $t" --data-binary '@-'
    } -ArgumentList $src, $dst, $TOKEN
} | Wait-Job | Receive-Job
```
* **Expected Output**: `All 10 parallel transfer jobs finished with zero deadlocks!`

---

### [TC-CON-03] Sustained Load Memory Stability (JVM Heap)
* **Objective**: Verify that JVM memory usage remains stable without uncollected object accumulation.
```powershell
# 1. Run 50 continuous mutations
1..50 | ForEach-Object {
    $body = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":1.00,`"idempotencyKey`":`"mem-$_`"}"
    $body | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' > $null
}

# 2. Query live JVM memory via Actuator
curl.exe -s http://localhost:8084/actuator/metrics/jvm.memory.used
```
* **Expected Output**: Returns memory in bytes. Heap stabilizes without runaway growth.

---

### [TC-CON-04] Connection Pool Exhaustion Guard (HikariCP)
* **Objective**: Verify that database connections are returned to the pool, preventing connection leaks.
```powershell
curl.exe -s http://localhost:8084/actuator/metrics/hikaricp.connections.active
```
* **Expected Output**: Returns `"measurements":[{"statistic":"VALUE","value":0.0}]` (all connections released).

---

## Suite 2: Identity & Security

### [TC-SEC-01] Customer Registration (KYC)
* **Objective**: Verify onboarding of a new retail customer in Oracle `CUSTOMER_MASTER` and `APP_USER_MASTER`.
```powershell
$newEmail = "user.$([guid]::NewGuid().ToString().Substring(0,8))@example.com"
$regBody = "{`"firstName`":`"Test`",`"lastName`":`"User`",`"email`":`"$newEmail`",`"contactNo`":`"+63-917-555-0100`",`"birthDate`":`"1990-01-01`",`"password`":`"Password123!`"}"
$regBody | curl.exe -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" --data-binary '@-'
```
* **Expected Output**: Status `201 Created` with a new `customerId` UUID.

---

### [TC-SEC-02] Duplicate Email Registration Guard
* **Objective**: Verify that duplicate emails are rejected by Oracle constraint `uq_customer_master_email`.
```powershell
$dupBody = '{"firstName":"Alice","lastName":"Reyes","email":"alice.reyes@example.com","contactNo":"+63-917-555-0100","birthDate":"1990-04-12","password":"StrongPass123!"}'
$dupBody | curl.exe -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" --data-binary '@-'
```
* **Expected Output**: `409 Conflict` ("A customer with email 'alice.reyes@example.com' already exists").

---

### [TC-SEC-03] Authentication & JWT Token Issuance
* **Objective**: Verify BCrypt credential authentication and HMAC-SHA384 JWT issuance.
```powershell
$loginBody = '{"email":"alice.reyes@example.com","password":"StrongPass123!"}'
$loginBody | curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" --data-binary '@-'
```
* **Expected Output**: `200 OK` with `tokenType: "Bearer"` and signed JWT token.

---

### [TC-SEC-04] Session Revocation & Token Blacklist
* **Objective**: Verify that after logout, the API Gateway immediately blocks the token via Redis.
```powershell
# 1. Logout
curl.exe -s -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"

# 2. Try calling balance endpoint with logged-out token (Include headers with -i)
curl.exe -i http://localhost:8080/api/accounts/$ACCT1/balance -H "Authorization: Bearer $TOKEN"
```
* **Expected Output**: `HTTP/1.1 401 Unauthorized`.

---

## Suite 3: Account Creation & Oracle Master

### [TC-ACC-01] Create Primary Savings Account
* **Objective**: Create a Savings account in Oracle `ACCOUNT_MASTER`.
```powershell
$acct1Body = "{`"customerId`":`"$CUSTOMER_ID`",`"accountType`":`"SAVINGS`",`"currencyCode`":`"PHP`"}"
$acct1Body | curl.exe -s -X POST http://localhost:8080/api/accounts -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: `201 Created`, `accountType: "SAVINGS"`, `balanceAmount: 0`, `accountStatus: "ACTIVE"`.

---

### [TC-ACC-02] Create Secondary Checking Account
* **Objective**: Create a Checking account for transfer testing.
```powershell
$acct2Body = "{`"customerId`":`"$CUSTOMER_ID`",`"accountType`":`"CHECKING`",`"currencyCode`":`"PHP`"}"
$acct2Body | curl.exe -s -X POST http://localhost:8080/api/accounts -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: `201 Created`, `accountType: "CHECKING"`.

---

## Suite 4: Core Balance Mutations & Double-Entry Accounting

### [TC-MUT-01] Single-Leg Deposit (Credit Leg)
* **Objective**: Deposit funds and verify atomic balance increment.
```powershell
$depBody = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":5000.00,`"idempotencyKey`":`"dep-$([guid]::NewGuid())`"}"
$depBody | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: `201 Created`, `txnStatus: "COMMITTED"`, `balanceAfter` increases by 5000.00.

---

### [TC-MUT-02] Single-Leg Withdrawal (Debit Leg)
* **Objective**: Withdraw funds and verify atomic balance decrement.
```powershell
$wdBody = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"WITHDRAWAL`",`"amount`":1500.00,`"idempotencyKey`":`"wd-$([guid]::NewGuid())`"}"
$wdBody | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: `201 Created`, `balanceAfter` decrements by 1500.00.

---

### [TC-MUT-03] Double-Entry Fund Transfer (Money Conservation)
* **Objective**: Transfer ₱1,500.50 from Account 1 to Account 2 and verify 2 rows in PostgreSQL.
```powershell
$trfBody = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":`"$ACCT2`",`"txnType`":`"TRANSFER`",`"amount`":1500.50,`"idempotencyKey`":`"trf-$([guid]::NewGuid())`"}"
$trfRes = $trfBody | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$LAST_TXN = $trfRes.data.txnId

# Fetch PostgreSQL Ledger Audit rows
curl.exe -s http://localhost:8080/api/v1/ledger/audit/$LAST_TXN -H "Authorization: Bearer $TOKEN"
```
* **Expected Output**: Returns **2 rows**: 1 `DEBIT` row for Account 1, 1 `CREDIT` row for Account 2 with `auditState: "COMMITTED"`.

---

### [TC-MUT-04] Idempotency Replay Guard (Redis)
* **Objective**: Verify that submitting duplicate requests with the same key returns the cached response without double-crediting.
```powershell
$key = "idem-replay-test-$([guid]::NewGuid())"
$body = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":100.00,`"idempotencyKey`":`"$key`"}"

# First Submission (Executes)
$body | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'

# Second Submission (Replay)
$body | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: Both calls return the exact same `txnId` and balance; balance is only incremented once.

---

## Suite 5: Invariants & Negative Constraints

### [TC-INV-01] Unauthorized Overdraft Prevention
* **Objective**: Verify that withdrawing more than the balance is rejected and preserves `balance_amount >= 0`.
```powershell
$odBody = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"WITHDRAWAL`",`"amount`":999999.00,`"idempotencyKey`":`"od-$([guid]::NewGuid())`"}"
$odBody | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: `422 Unprocessable Entity` (`InsufficientBalanceException`).

---

### [TC-INV-02] Self-Transfer Rejection
* **Objective**: Verify that transfer between the same account is blocked.
```powershell
$selfBody = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":`"$ACCT1`",`"txnType`":`"TRANSFER`",`"amount`":50.00,`"idempotencyKey`":`"self-$([guid]::NewGuid())`"}"
$selfBody | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: `400 Bad Request` ("Source and destination accounts must differ").

---

### [TC-INV-03] Negative Amount Rejection
* **Objective**: Verify that non-positive transaction amounts are rejected.
```powershell
$negBody = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":-100.00,`"idempotencyKey`":`"neg-$([guid]::NewGuid())`"}"
$negBody | curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'
```
* **Expected Output**: `400 Bad Request` (JSR-380 validation `@Positive`).

---

## Suite 6: PostgreSQL Regulatory Audit & Immutability

### [TC-AUD-01] PostgreSQL Trigger Blocks UPDATE
* **Objective**: Verify that `LEDGER_MUTATION_AUDIT` records cannot be updated even via raw SQL.
```powershell
docker exec -it postgres-db psql -U ledger_audit -d ledger_audit -c "UPDATE ledger_mutation_audit SET mutation_amount = 0 WHERE txn_type = 'DEPOSIT';"
```
* **Expected Output**:
  ```text
  ERROR: ledger_mutation_audit is append-only: UPDATE not permitted on mutation_uuid ...
  ```

---

### [TC-AUD-02] PostgreSQL Trigger Blocks DELETE
* **Objective**: Verify that `LEDGER_MUTATION_AUDIT` records cannot be deleted.
```powershell
docker exec -it postgres-db psql -U ledger_audit -d ledger_audit -c "DELETE FROM ledger_mutation_audit WHERE txn_type = 'TRANSFER';"
```
* **Expected Output**:
  ```text
  ERROR: ledger_mutation_audit is append-only: DELETE not permitted on mutation_uuid ...
  ```

---

## Suite 7: Event Streaming & Notifications

### [TC-EVT-01] Notification Audit Trail in PostgreSQL
* **Objective**: Verify that `notification-service` consumes Kafka events and writes to `NOTIFICATION_AUDIT`.
```powershell
docker exec -it postgres-db psql -U ledger_audit -d ledger_audit -c "SELECT notif_id, customer_id, status, created_at FROM notification_audit ORDER BY created_at DESC LIMIT 3;"
```
* **Expected Output**: Returns the latest notification rows with `status = 'SENT'`.

---

## Master Automated Test Execution

To run all of the above tests automatically in 4 seconds and produce a fresh Excel CSV report:
```powershell
.\run_test_suite.ps1
```
