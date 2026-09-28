# Capstone Banking System - Testing Instructions & Verification Guide

This document contains complete, step-by-step testing instructions, automated test suite runners, and verification commands for the **Core Retail Ledger & Balance Mutation Engine**.

---

## 1. Automated Test Suite (Recommended)

You can run the entire automated test suite with a single command. It automatically verifies infrastructure health, registration, JWT authentication, account creation, double-entry balance mutations, overdraft protection, PostgreSQL audit immutability triggers, and session revocation.

```powershell
.\run_test_suite.ps1
```

* **Execution Time**: ~4 seconds
* **Output Report**: Automatically exports detailed results to `test_results_report.csv` (openable in Excel).

---

## 2. Test Documentation & Excel Artifacts

All test specifications and execution logs are pre-compiled and available in the project root:

| File | Purpose |
| :--- | :--- |
| [**`capstone_test_cases.csv`**](capstone_test_cases.csv) | Master Excel spreadsheet containing all 21 test case specifications. |
| [**`TEST_CASES_COMMANDS.md`**](TEST_CASES_COMMANDS.md) | Dedicated guide with copy-pasteable PowerShell commands for each test case. |
| [**`run_test_suite.ps1`**](run_test_suite.ps1) | Automated test runner script. |
| [**`test_results_report.csv`**](test_results_report.csv) | Execution scorecard generated from the automated test run. |

---

## 3. Java Concurrency & Lock Verification Test

To verify that Oracle's pessimistic row locks (`SELECT ... FOR UPDATE`) prevent race conditions, lost updates, and overdrafts under simultaneous load:

```powershell
mvn test -pl transaction-service -Dtest=ParallelWithdrawalConcurrencyTest
```

* **What it does**: Fires 10 concurrent threads simultaneously using Java's `CountDownLatch`.
* **Assertion**: Proves that all parallel debits serialize cleanly, balance remains $\ge 0$, and expected balance equals actual balance.

---

## 4. Manual Step-by-Step Terminal Commands

### Step 0: Setup Environment Variables (Run Once First)
```powershell
# Authenticate and get JWT Token
$login = '{"email":"alice.reyes@example.com","password":"StrongPass123!"}' | curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" --data-binary '@-' | ConvertFrom-Json
$TOKEN = $login.data.token
$CUSTOMER_ID = $login.data.customerId

# Fetch Account 1 & Account 2
$accts = curl.exe -s http://localhost:8080/api/accounts/customer/$CUSTOMER_ID -H "Authorization: Bearer $TOKEN" | ConvertFrom-Json
$ACCT1 = $accts.data[0].accountId
$ACCT2 = $accts.data[1].accountId

Write-Host "Setup Completed! Token: $TOKEN" -ForegroundColor Green
```

### Core Tests Quick Reference

| Test Case | Description | Command |
| :--- | :--- | :--- |
| **Deposit** | Deposit ₱5,000 | `"{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":5000.00,`"idempotencyKey`":`"dep-$([guid]::NewGuid())`"}" \| curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'` |
| **Transfer** | Transfer ₱1,500.50 (Acct 1 $\rightarrow$ Acct 2) | `"{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":`"$ACCT2`",`"txnType`":`"TRANSFER`",`"amount`":1500.50,`"idempotencyKey`":`"trf-$([guid]::NewGuid())`"}" \| curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'` |
| **Audit Trail** | Verify 2 rows in PostgreSQL | `curl.exe -s http://localhost:8080/api/v1/ledger/audit/<txnId> -H "Authorization: Bearer $TOKEN"` |
| **Overdraft Guard** | Withdraw ₱10,000 (Expect 422) | `"{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"WITHDRAWAL`",`"amount`":10000.00,`"idempotencyKey`":`"od-01`"}" \| curl.exe -s -X POST http://localhost:8080/api/v1/ledger/mutate -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-'` |
| **Postgres Immutability** | Trigger blocks UPDATE | `docker exec -it postgres-db psql -U ledger_audit -d ledger_audit -c "UPDATE ledger_mutation_audit SET mutation_amount = 0 WHERE txn_type = 'DEPOSIT';"` |
| **Logout** | Invalidate token in Redis | `curl.exe -s -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"` |
