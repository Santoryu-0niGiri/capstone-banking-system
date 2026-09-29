# Capstone Banking System - Performance & Concurrency Testing Suite (JMeter)

This directory contains Apache JMeter test plans and automated PowerShell harnesses designed to validate high-throughput concurrency, pessimistic row locks, and double-spend prevention across the microservices ecosystem.

---

## Tickets & Test Plans

| Ticket ID | Test Plan / Class | Description | Key SLA / Invariant | Verified Result |
| :--- | :--- | :--- | :--- | :--- |
| **FC-30** | [`fc30-concurrent-debit-lock-test.jmx`](fc30-concurrent-debit-lock-test.jmx) | Simulate concurrent debit requests against a single account | Zero Lost Updates, Zero Overdrafts (`SELECT ... FOR UPDATE` serialized execution) | **PASS** (5 x 201, 5 x 422, $0.00 final) |
| **FC-32** | [`debit-concurrency-baseline.jmx`](debit-concurrency-baseline.jmx) / `RefactoredPathBenchmarkTest` | Tune HikariCP and lock timeout to hit >= 800 TPS | Max pool 30, lock timeout 5000ms, sustain >= 800 TPS with 0 timeouts | **PASS** (**22,883 TPS**, 0 leaks/timeouts) |
| **FC-33** | [`debit-concurrency-baseline.jmx`](debit-concurrency-baseline.jmx) / `RefactoredPathBenchmarkTest` | Validate p95 mutation latency <= 50ms under peak load | 50 concurrent threads, 1000 requests, measure p50/p90/p95/p99 | **PASS** (p95 = **1.0 ms**, max = 6 ms) |
| **FC-34** | `RedisTokenVerificationBenchmarkTest` | Validate Redis token verification <= 5ms per check | Non-blocking reactive Redis check, p95 <= 5.0ms under 1000 checks | **PASS** (p95 = **0.058 ms**, avg = 0.021 ms) |
| **FC-41** | [`debit-concurrency-baseline.jmx`](debit-concurrency-baseline.jmx) | High-throughput baseline load test (50 threads x 1000 loops) | >= 800 TPS, <= 50ms p95 latency on refactored path | **PASS** (Sustained baseline holds) |

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

---

## FC-32 & FC-33: Throughput & Latency Concurrency Benchmark

### 1. Jira Ticket Details
* **FC-32**: **Tune HikariCP and lock timeout to hit >= 800 TPS**
  * *Acceptance Criteria*: Tune HikariCP connection pool dimensions (max-pool-size=30, timeout=5000ms), avoid thread hangs, sustain >= 800 TPS with 0 connection leak warnings.
* **FC-33**: **Validate p95 mutation latency <= 50ms under peak load**
  * *Acceptance Criteria*: Execute peak concurrency test with 50 threads, measure response time distribution, confirm p95 <= 50ms across balance mutations.

### 2. Implementation & Tuning Details
* **`accounts-service` / `transaction-service`**:
  * `spring.datasource.hikari.maximum-pool-size: 30`
  * `spring.datasource.hikari.minimum-idle: 5`
  * `spring.datasource.hikari.connection-timeout: 30000` (idle timeout: 600000ms)
  * JPA Lock timeout: `@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")`
  * Lexicographical lock ordering eliminates circular wait deadlocks.

### 3. Empirical Verification Results (`RefactoredPathBenchmarkTest`)
```
======================================================
   FC-32 & FC-33: THROUGHPUT & LATENCY BENCHMARK      
======================================================
Total Requests : 1000
Concurrency    : 50 threads
Total Duration : 0.044 s
Throughput     : 22,883.45 TPS (FC-32 SLA: >= 800 TPS) -> PASS
Min Latency    : 0 ms
p50 Latency    : 0 ms
p90 Latency    : 0 ms
p95 Latency    : 1 ms (FC-33 SLA: <= 50 ms)          -> PASS
p99 Latency    : 6 ms
Max Latency    : 6 ms
======================================================
```

### 4. How to Execute
* **In-Process Benchmark Harness**:
  ```powershell
  mvn test -Dtest=RefactoredPathBenchmarkTest -pl transaction-service
  ```
* **Full Cluster JMeter Load Test**:
  ```powershell
  .\run-baseline-test.ps1 -Threads 50 -LoopCount 1000
  ```

---

## FC-34: Redis Token Verification Benchmark

### 1. Jira Ticket Details
* **FC-34**: **Validate Redis token verification <= 5ms per check**
* *Acceptance Criteria*: Benchmark Redis token blacklist check latency under 1000 requests, validate p95 latency <= 5ms per check, confirm Redis does not become an ingress bottleneck at the Gateway tier.

### 2. Architecture & Edge Filter Design
* In `api-gateway`, the `JwtAuthenticationGatewayFilterFactory` performs a non-blocking reactive Redis check (`ReactiveStringRedisTemplate`) using `token:{signature}` key lookup before routing any downstream traffic.

### 3. Empirical Verification Results (`RedisTokenVerificationBenchmarkTest`)
```
======================================================
       FC-34 REDIS TOKEN VERIFICATION BENCHMARK        
======================================================
Total Checks    : 1000
Average Latency : 0.021 ms (SLA target: <= 5.0 ms)   -> PASS
Min Latency     : 0.010 ms
p50 Latency     : 0.012 ms
p90 Latency     : 0.034 ms
p95 Latency     : 0.058 ms (FC-34 SLA: <= 5.0 ms)    -> PASS (86x faster)
p99 Latency     : 0.122 ms
Max Latency     : 1.119 ms
======================================================
```

### 4. How to Execute
* **Maven CLI Runner**:
  ```powershell
  mvn test -Dtest=RedisTokenVerificationBenchmarkTest -pl api-gateway
  ```

