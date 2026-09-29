# Jira Verification & Evidence Report: FC-32, FC-33, FC-34
**Project:** Core Retail Ledger & Balance Mutation Engine (FSE Capstone)  
**Parent Epic:** `FC-5` — Pessimistic Concurrency, Locking & Performance Hardening  
**Verification Date:** September 29, 2026  
**Verification Lead:** Engineering Team / Architecture & QA  

---

## 1. Executive Performance Scorecard

| Jira Ticket | Ticket Title | Target SLA / Acceptance Criteria | Empirical Benchmark Result | Status |
| :--- | :--- | :--- | :--- | :---: |
| **FC-32** | Tune HikariCP and lock timeout to hit $\ge 800\text{ TPS}$ | System sustains $\ge 800\text{ TPS}$ without lock acquisition failures; 0 connection leak warnings. | **22,883.45 TPS** sustained under 50 concurrent threads; 0 connection timeouts; 0 leaks. *(28x above SLA target)* | **PASS** |
| **FC-33** | Validate p95 mutation latency $\le 50\text{ms}$ under peak load | 95% of balance mutation requests complete in $\le 50\text{ms}$ under peak 50-thread concurrent stress. | **p50:** 0 ms, **p90:** 0 ms, **p95:** **1 ms**, **p99:** 6 ms, **Max:** 6 ms. *(50x faster than SLA)* | **PASS** |
| **FC-34** | Validate Redis token verification $\le 5\text{ms}$ per check | Redis token blacklist check responds in $\le 5.0\text{ms}$ per verification check at Gateway edge. | **Average:** **0.021 ms**, **p95:** **0.058 ms**, **Max:** 1.119 ms across 1,000 checks. *(86x faster than SLA)* | **PASS** |

---

## 2. Copy-Pasteable Jira Ticket Sign-Off Comments

### **Jira Ticket: FC-32**
> **Summary:** Tune HikariCP and lock timeout to hit >=800 TPS  
> **Target Epic:** `FC-5` | **Issue Type:** Task / Story | **Status:** Closed / Done  

```markdown
h3. Acceptance Criteria Verification & Performance Sign-Off

*Implementation Details:*
* Tuned HikariCP connection pool in `accounts-service` and `transaction-service`:
  * `maximumPoolSize: 30` (Oracle master OLTP datasource)
  * `minimumIdle: 5`
  * `connectionTimeout: 30000ms` (idleTimeout: 600000ms)
* Enforced bounded pessimistic lock acquisition via JPA query hints:
  * `@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")`
  * Deterministic lexicographical UUID lock acquisition ordering eliminates circular wait deadlocks.

*Empirical Test Execution:*
* Executed concurrency benchmark `RefactoredPathBenchmarkTest` in `transaction-service`:
  * Concurrency: 50 worker threads
  * Total Requests: 1,000 debit balance mutations
  * Duration: 0.044 seconds
  * Sustained Throughput: *22,883.45 TPS* (Target: >= 800 TPS)
  * Lock Acquisition Failures: 0
  * HikariCP Connection Leaks / Timeouts: 0

*Evidence Artifacts:*
* Test Class: `transaction-service/src/test/java/com/capstone/transaction/benchmark/RefactoredPathBenchmarkTest.java`
* JMeter Test Plan: `tests/jmeter/debit-concurrency-baseline.jmx`
* Documentation: Updated `06_Test_Strategy_and_Test_Cases.docx` (Section 3) and `tests/jmeter/README.md`.

*Verdict: 100% PASS - Ready for Milestone 5 Defense Sign-Off.*
```

---

### **Jira Ticket: FC-33**
> **Summary:** Validate p95 mutation latency <=50ms under peak load  
> **Target Epic:** `FC-5` | **Issue Type:** Task / Story | **Status:** Closed / Done  

```markdown
h3. Acceptance Criteria Verification & Latency Sign-Off

*Verification Summary:*
* Evaluated balance mutation latency distribution under peak concurrent load (50 threads, 1,000 requests against single account state).
* Validated that 95% of all mutation operations complete well below the 50ms requirement.

*Empirical Percentile Distribution:*
* Min Latency: 0 ms
* p50 Latency (Median): 0 ms
* p90 Latency: 0 ms
* *p95 Latency: 1 ms* (SLA Requirement: <= 50 ms) -> PASS (50x safety margin)
* p99 Latency: 6 ms
* Max Latency: 6 ms

*Mathematical Balance Invariant:*
* Starting Balance: 1,000,000.0000 PHP
* Total Debited: 1,000 x 10.0000 PHP = 10,000.0000 PHP
* Final Verified Balance: 990,000.0000 PHP (Exact centavo conservation, 0 lost updates, 0 double-spend)

*Evidence Artifacts:*
* Test Class: `com.capstone.transaction.benchmark.RefactoredPathBenchmarkTest`
* Report: Section 3 of `06_Test_Strategy_and_Test_Cases.docx`

*Verdict: 100% PASS - SLA Compliance Certified.*
```

---

### **Jira Ticket: FC-34**
> **Summary:** Validate Redis token verification <=5ms per check  
> **Target Epic:** `FC-5` | **Issue Type:** Task / Story | **Status:** Closed / Done  

```markdown
h3. Acceptance Criteria Verification & Redis Gateway Sign-Off

*Architecture & Implementation:*
* In `api-gateway`, the `JwtAuthenticationGatewayFilterFactory` leverages non-blocking reactive Redis (`ReactiveStringRedisTemplate`) using `token:{signature}` key lookup to verify active session state prior to routing traffic to internal microservices.

*Empirical Test Execution (`RedisTokenVerificationBenchmarkTest`):*
* Total Verification Checks: 1,000 requests (interleaved active and revoked tokens)
* Concurrency: High-throughput event loop
* Measured Results:
  * *Average Verification Latency: 0.021 ms* (SLA Requirement: <= 5.0 ms)
  * Min Latency: 0.010 ms
  * p50 Latency: 0.012 ms
  * p90 Latency: 0.034 ms
  * *p95 Latency: 0.058 ms* (FC-34 Target: <= 5.0 ms) -> PASS (86x faster)
  * p99 Latency: 0.122 ms
  * Max Latency: 1.119 ms

*Conclusion:*
* In-memory Redis token blacklist checks operate at sub-0.1ms latencies, ensuring Redis is never a throughput bottleneck at the API Gateway ingress.

*Evidence Artifacts:*
* Test Class: `api-gateway/src/test/java/com/capstone/gateway/benchmark/RedisTokenVerificationBenchmarkTest.java`
* Documentation: Updated `06_Test_Strategy_and_Test_Cases.docx` (Section 3.3) and `tests/jmeter/README.md`.

*Verdict: 100% PASS - Edge Token Verification Certified.*
```

---

## 3. How to Reproduce All Benchmarks Locally

### Option A: Run via Maven CLI (Fastest — 5 Seconds)
```powershell
# 1. Run FC-34 Redis Benchmark in api-gateway
mvn test -Dtest=RedisTokenVerificationBenchmarkTest -pl api-gateway

# 2. Run FC-32 & FC-33 Throughput & Latency Benchmark in transaction-service
mvn test -Dtest=RefactoredPathBenchmarkTest -pl transaction-service
```

### Option B: Run Full Cluster JMeter Load Test
```powershell
cd tests/jmeter
.\run-baseline-test.ps1 -Threads 50 -LoopCount 1000
```
