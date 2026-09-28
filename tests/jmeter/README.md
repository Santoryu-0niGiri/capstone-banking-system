# Capstone Banking System - Performance & Concurrency Testing Suite (JMeter)

This directory contains Apache JMeter test plans and automated PowerShell harnesses designed to validate high-throughput concurrency, pessimistic row locks, and double-spend prevention across the microservices ecosystem.

---

## Tickets & Test Plans

| Ticket ID | Test Plan (.jmx) | Description | Key SLA / Invariant |
| :--- | :--- | :--- | :--- |
| **FC-30** | [`fc30-concurrent-debit-lock-test.jmx`](fc30-concurrent-debit-lock-test.jmx) | **Simulate concurrent debit requests against a single account** | **Zero Lost Updates**, **Zero Overdrafts** (`SELECT ... FOR UPDATE` serialized execution) |
| **FC-41** | [`debit-concurrency-baseline.jmx`](debit-concurrency-baseline.jmx) | High-throughput baseline load test (50 threads x 1000 loops) | >= 800 TPS, <= 50ms p95 latency |

---

## FC-30: Concurrent Debit Thread Group (Pessimistic Locking Verification)

### 1. Jira Ticket Details
* **Epic / Parent**: FC-5 (Pessimistic Concurrency & Double-Spend Protection)
* **Ticket ID**: `FC-30`
* **Title**: **Build JMeter thread group simulating concurrent debit requests**
* **Acceptance Criteria**: *Script executes parallel withdrawals against single accounts to test concurrency locks.*

### 2. Architecture & Concurrency Design
When multiple concurrent debit requests target the same account simultaneously:
```
                      +---------------------------------------+
                      |   JMeter Synchronizing Timer (10 thr) |
                      +---------------------------------------+
                                          |
                      (Simultaneous Strike at Millisecond T0)
                                          v
                      +---------------------------------------+
                      |  Spring Cloud Gateway (:8080)         |
                      +---------------------------------------+
                                          |
                                          v
                      +---------------------------------------+
                      |  Transaction Service (:8084)          |
                      |  POST /api/v1/ledger/mutate           |
                      +---------------------------------------+
                                          |
             Pessimistic Row Lock: SELECT ... FOR UPDATE on ACCOUNT_MASTER
                                          |
                 +------------------------+------------------------+
                 |                                                 |
         Threads 1 - 5 (Balance >= 100)                     Threads 6 - 10 (Balance = 0)
                 |                                                 |
        [HTTP 201 CREATED]                               [HTTP 422 UNPROCESSABLE]
  500 PHP total debited (5 x 100)                     RFC-7807: Insufficient Balance
                 \                                                 /
                  +-----------------------+-----------------------+
                                          |
                                          v
                       Oracle XE Balance Invariant: $0.00
                       Zero Lost Updates | Zero Negative Balance
```

### 3. JMeter Test Plan Hierarchy (`fc30-concurrent-debit-lock-test.jmx`)

1. **User Defined Variables**:
   * `HOST` (`localhost`), `PORT` (`8080`), `THREADS` (`10`)
   * `WITHDRAW_AMOUNT` (`100.00`), `INITIAL_DEPOSIT` (`500.00`)
   * `ACCOUNT_ID`, `JWT_TOKEN` (optional; if blank, auto-provisioned)

2. **`setUp: Seed & Fund Test Account` (1 Thread x 1 Loop)**:
   * **01. Register New Test Customer**: `POST /api/auth/register` (generates unique customer identity).
   * **02. Login Test Customer**: `POST /api/auth/login` (extracts HMAC-SHA384 Bearer JWT).
   * **03. Create Demand Deposit Account**: `POST /api/accounts` (extracts newly provisioned `accountId`).
   * **04. Fund Account with Initial Deposit**: `POST /api/v1/ledger/mutate` (credits exactly 500.00 PHP).

3. **`FC-30 Thread Group: Concurrent Debit Requests` (10 Concurrent Threads)**:
   * **`Synchronizing Timer` (Rendezvous Point)**: Holds all 10 threads until all 10 are initialized, releasing them simultaneously at the exact same millisecond.
   * **`HTTP Header Manager`**: Attaches Bearer JWT and unique `${__UUID}` `Idempotency-Key` per thread.
   * **`POST /api/v1/ledger/mutate`**: Parallel withdrawals of 100.00 PHP each.
   * **`Response Assertion`**: Regex matches `201|422` with `assume_success=true`. (Both 201 and 422 are verified legitimate business outcomes; any 500/504 indicates lock contention failure).
   * **`JSR223 PostProcessor`**: Atomically aggregates success (201), insufficient fund (422), and error counts.

4. **`tearDown: Verify Invariants & Balance Integrity` (1 Thread x 1 Loop)**:
   * **05. Query Final Account Balance**: `GET /api/accounts/{id}/balance`.
   * **`JSR223 Assertion`**: Mathematically verifies:
     `Final Balance = Initial Deposit - (Success Count * Withdraw Amount)`
     Asserts `Final Balance >= 0.00`, confirming zero lost updates and zero overdrafts.

---

## How to Run the Tests

### Option A: 1-Click Automated CLI Runner (Recommended)

Run the PowerShell harness from `tests/jmeter/`:
```powershell
.\run-fc30-test.ps1
```

To run with an automated HTML performance dashboard report:
```powershell
.\run-fc30-test.ps1 -GenerateReport
```

To test with custom threads and amounts against an existing account:
```powershell
.\run-fc30-test.ps1 -Threads 20 -WithdrawAmount 50.00 -InitialDeposit 1000.00
```

### Option B: Visual GUI Mode (Interactive Inspection)

Launch the test plan inside Apache JMeter GUI:
```powershell
.\run-fc30-test.ps1 -OpenGui
```
*Or directly via JMeter CLI:*
```powershell
jmeter -t fc30-concurrent-debit-lock-test.jmx
```
1. Click **View Results Tree** in the left panel.
2. Click the green **Start** button (or press `Ctrl+R`).
3. Observe all 10 concurrent requests executing simultaneously with 5 x `201 Created` and 5 x `422 Unprocessable Entity`, followed by the `tearDown` thread group confirming `0.00 PHP` remaining.
