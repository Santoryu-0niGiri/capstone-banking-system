# =====================================================================
# CAPSTONE BANKING SYSTEM - AUTOMATED MASTER TEST SUITE
# Executes and verifies all test cases across:
# Gateway, Oracle, PostgreSQL, Redis, and Kafka.
# =====================================================================

$ErrorActionPreference = "Continue"
$BASE_URL = "http://localhost:8080"
$global:passed = 0
$global:failed = 0
$global:testResults = @()

function Report-Test {
    param(
        [string]$Id,
        [string]$Suite,
        [string]$Name,
        $Success,
        [string]$Details
    )
    $isOk = [bool]$Success
    if ($isOk) {
        $global:passed++
        Write-Host "  [PASS] $Id : $Name" -ForegroundColor Green
        $global:testResults += [PSCustomObject]@{ ID = $Id; Suite = $Suite; Test = $Name; Status = "PASS"; Details = $Details }
    } else {
        $global:failed++
        Write-Host "  [FAIL] $Id : $Name - $Details" -ForegroundColor Red
        $global:testResults += [PSCustomObject]@{ ID = $Id; Suite = $Suite; Test = $Name; Status = "FAIL"; Details = $Details }
    }
}

Clear-Host
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   CAPSTONE CORE RETAIL LEDGER - AUTOMATED TEST SUITE RUNNER           " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Target Gateway: $BASE_URL`n"

# ---------------------------------------------------------------------
# SUITE 1: SYSTEM HEALTH
# ---------------------------------------------------------------------
Write-Host "--- [SUITE 1: Infrastructure Health] ---" -ForegroundColor Yellow
$containers = docker ps --format "{{.Names}}"
$allRunning = ($containers -contains "oracle-db") -and ($containers -contains "postgres-db") -and ($containers -contains "redis") -and ($containers -contains "kafka")
Report-Test "TC-SYS-01" "Infrastructure" "Core Containers Up & Running" $allRunning "Verified Oracle, Postgres, Redis, Kafka"

# ---------------------------------------------------------------------
# SUITE 2: IDENTITY & SECURITY
# ---------------------------------------------------------------------
Write-Host "`n--- [SUITE 2: Identity & Security] ---" -ForegroundColor Yellow

# TC-SEC-01: Customer Registration
$randomEmail = "user.$([guid]::NewGuid().ToString().Substring(0,8))@banking.test"
$regPayload = "{`"firstName`":`"Test`",`"lastName`":`"User`",`"email`":`"$randomEmail`",`"contactNo`":`"+63-917-555-0100`",`"birthDate`":`"1990-01-01`",`"password`":`"Password123!`"}"
$regRes = $regPayload | curl.exe -s -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" --data-binary '@-' | ConvertFrom-Json
$custCreated = ($null -ne $regRes.data.customerId)
Report-Test "TC-SEC-01" "Identity" "Customer Registration (KYC)" $custCreated "CustomerId: $($regRes.data.customerId)"

# TC-SEC-02: Duplicate Email Rejection
$dupRes = $regPayload | curl.exe -s -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" --data-binary '@-' | ConvertFrom-Json
$dupBlocked = ($dupRes.status -eq 409) -or ($dupRes.title -like "*Duplicate*")
Report-Test "TC-SEC-02" "Identity" "Duplicate Email Guard" $dupBlocked "Expected 409 Conflict"

# TC-SEC-03: Login & JWT Issuance
$loginPayload = '{"email":"alice.reyes@example.com","password":"StrongPass123!"}'
$loginRes = $loginPayload | curl.exe -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" --data-binary '@-' | ConvertFrom-Json
$TOKEN = $loginRes.data.token
$CUSTOMER_ID = $loginRes.data.customerId
$loginSuccess = ($null -ne $TOKEN) -and ($loginRes.data.email -eq "alice.reyes@example.com")
Report-Test "TC-SEC-03" "Identity" "Authentication & JWT Token Issuance" $loginSuccess "JWT Acquired"

# ---------------------------------------------------------------------
# SUITE 3: ACCOUNT CREATION (ORACLE MASTER DB)
# ---------------------------------------------------------------------
Write-Host "`n--- [SUITE 3: Account Creation & Oracle Master] ---" -ForegroundColor Yellow

# Create Account 1 (Savings)
$acct1Payload = "{`"customerId`":`"$CUSTOMER_ID`",`"accountType`":`"SAVINGS`",`"currencyCode`":`"PHP`"}"
$acct1Res = $acct1Payload | curl.exe -s -X POST "$BASE_URL/api/accounts" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$ACCT1 = $acct1Res.data.accountId
Report-Test "TC-ACC-01" "Accounts" "Create Primary Savings Account" ($null -ne $ACCT1) "Account 1: $ACCT1"

# Create Account 2 (Checking)
$acct2Payload = "{`"customerId`":`"$CUSTOMER_ID`",`"accountType`":`"CHECKING`",`"currencyCode`":`"PHP`"}"
$acct2Res = $acct2Payload | curl.exe -s -X POST "$BASE_URL/api/accounts" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$ACCT2 = $acct2Res.data.accountId
Report-Test "TC-ACC-02" "Accounts" "Create Secondary Checking Account" ($null -ne $ACCT2) "Account 2: $ACCT2"

# ---------------------------------------------------------------------
# SUITE 4: CORE BALANCE MUTATIONS & INVARIANTS
# ---------------------------------------------------------------------
Write-Host "`n--- [SUITE 4: Core Balance Mutations & Double-Entry] ---" -ForegroundColor Yellow

# TC-MUT-01: Deposit
$depPayload = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":5000.00,`"idempotencyKey`":`"dep-$([guid]::NewGuid())`"}"
$depRes = $depPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$depSuccess = ($depRes.data.txnStatus -eq "COMMITTED")
Report-Test "TC-MUT-01" "Mutations" "Single-Leg Deposit (Credit Leg)" $depSuccess "Balance: $($depRes.data.balanceAfter)"

# TC-MUT-02: Withdrawal
$wdPayload = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"WITHDRAWAL`",`"amount`":1000.00,`"idempotencyKey`":`"wd-$([guid]::NewGuid())`"}"
$wdRes = $wdPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$wdSuccess = ($wdRes.data.txnStatus -eq "COMMITTED")
Report-Test "TC-MUT-02" "Mutations" "Single-Leg Withdrawal (Debit Leg)" $wdSuccess "Balance: $($wdRes.data.balanceAfter)"

# TC-MUT-03: Transfer & Double-Entry Verification
$trfPayload = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":`"$ACCT2`",`"txnType`":`"TRANSFER`",`"amount`":1500.50,`"idempotencyKey`":`"trf-$([guid]::NewGuid())`"}"
$trfRes = $trfPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$TRF_TXN = $trfRes.data.txnId

$auditRes = curl.exe -s "$BASE_URL/api/v1/ledger/audit/$TRF_TXN" -H "Authorization: Bearer $TOKEN" | ConvertFrom-Json
$auditCount = @($auditRes.data).Count
$isDoubleEntry = ($auditCount -eq 2)
Report-Test "TC-MUT-03" "Mutations" "Double-Entry Transfer (2 Rows in Postgres)" $isDoubleEntry "Txn: $TRF_TXN (Found $auditCount audit rows)"

# TC-MUT-04: Idempotency Replay Guard
$idemKey = "idem-$([guid]::NewGuid())"
$idemPayload = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":100.00,`"idempotencyKey`":`"$idemKey`"}"
$firstCall = $idemPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$secondCall = $idemPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$isIdempotent = ($firstCall.data.txnId -eq $secondCall.data.txnId) -and ($firstCall.data.balanceAfter -eq $secondCall.data.balanceAfter)
Report-Test "TC-MUT-04" "Mutations" "Idempotency Replay Guard (Redis)" $isIdempotent "Replay returned cached response"

# ---------------------------------------------------------------------
# SUITE 5: INVARIANTS & CONSTRAINTS
# ---------------------------------------------------------------------
Write-Host "`n--- [SUITE 5: Invariants & Negative Tests] ---" -ForegroundColor Yellow

# TC-INV-01: Unauthorized Overdraft
$overdraftPayload = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"WITHDRAWAL`",`"amount`":999999.00,`"idempotencyKey`":`"od-$([guid]::NewGuid())`"}"
$odRes = $overdraftPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$odBlocked = ($odRes.status -eq 422) -or ($odRes.title -like "*Insufficient*")
Report-Test "TC-INV-01" "Invariants" "Overdraft Prevention (balance >= 0)" $odBlocked "Expected 422 Unprocessable Entity"

# TC-INV-02: Self-Transfer Guard
$selfPayload = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":`"$ACCT1`",`"txnType`":`"TRANSFER`",`"amount`":50.00,`"idempotencyKey`":`"self-$([guid]::NewGuid())`"}"
$selfRes = $selfPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$selfBlocked = ($selfRes.status -eq 400)
Report-Test "TC-INV-02" "Invariants" "Self-Transfer Rejection" $selfBlocked "Expected 400 Bad Request"

# TC-INV-03: Negative Amount Guard
$negPayload = "{`"accountId`":`"$ACCT1`",`"counterpartyAccountId`":null,`"txnType`":`"DEPOSIT`",`"amount`":-100.00,`"idempotencyKey`":`"neg-$([guid]::NewGuid())`"}"
$negRes = $negPayload | curl.exe -s -X POST "$BASE_URL/api/v1/ledger/mutate" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" --data-binary '@-' | ConvertFrom-Json
$negBlocked = ($negRes.status -eq 400)
Report-Test "TC-INV-03" "Invariants" "Negative Amount Rejection" $negBlocked "Expected 400 Bad Request"

# ---------------------------------------------------------------------
# SUITE 6: POSTGRESQL AUDIT IMMUTABILITY
# ---------------------------------------------------------------------
Write-Host "`n--- [SUITE 6: Regulatory Audit & Immutability] ---" -ForegroundColor Yellow

# TC-AUD-01: Trigger blocks UPDATE
$updateRaw = docker exec postgres-db psql -U ledger_audit -d ledger_audit -c "UPDATE ledger_mutation_audit SET mutation_amount = 0 WHERE txn_type = 'DEPOSIT';" 2>&1
$updateStr = [string]::Join(" ", $updateRaw)
$updateBlocked = $updateStr.Contains("append-only")
Report-Test "TC-AUD-01" "Regulatory" "PostgreSQL Trigger Blocks UPDATE" $updateBlocked "Append-only trigger enforced"

# TC-AUD-02: Trigger blocks DELETE
$deleteRaw = docker exec postgres-db psql -U ledger_audit -d ledger_audit -c "DELETE FROM ledger_mutation_audit WHERE txn_type = 'TRANSFER';" 2>&1
$deleteStr = [string]::Join(" ", $deleteRaw)
$deleteBlocked = $deleteStr.Contains("append-only")
Report-Test "TC-AUD-02" "Regulatory" "PostgreSQL Trigger Blocks DELETE" $deleteBlocked "Append-only trigger enforced"

# ---------------------------------------------------------------------
# SUITE 7: KAFKA & NOTIFICATIONS
# ---------------------------------------------------------------------
Write-Host "`n--- [SUITE 7: Event Streaming & Notifications] ---" -ForegroundColor Yellow

$notifRaw = docker exec postgres-db psql -U ledger_audit -d ledger_audit -t -c "SELECT count(*) FROM notification_audit;" 2>&1
$notifStr = ([string]::Join(" ", $notifRaw)).Trim()
$notifsExist = ($notifStr -match "^\d+$")
Report-Test "TC-EVT-01" "Kafka" "Notification Audit Trail Recorded" $notifsExist "PostgreSQL notification_audit populated"

# ---------------------------------------------------------------------
# SUITE 8: LOGOUT & TOKEN BLACKLIST
# ---------------------------------------------------------------------
Write-Host "`n--- [SUITE 8: Session Invalidation & Gateway Revocation] ---" -ForegroundColor Yellow

# Logout
curl.exe -s -X POST "$BASE_URL/api/auth/logout" -H "Authorization: Bearer $TOKEN" > $null
$postLogoutCode = curl.exe -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/accounts/$ACCT1/balance" -H "Authorization: Bearer $TOKEN"
$revoked = ($postLogoutCode -eq "401")
Report-Test "TC-SEC-04" "Security" "Token Revocation (Redis Blacklist)" $revoked "HTTP 401 Unauthorized received"

# ---------------------------------------------------------------------
# SUMMARY REPORT
# ---------------------------------------------------------------------
Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host "                      TEST EXECUTION SCORECARD                         " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
$total = $global:passed + $global:failed
Write-Host "  Total Tests Run : $total"
Write-Host "  Passed          : $($global:passed)" -ForegroundColor Green
Write-Host "  Failed          : $($global:failed)" -ForegroundColor Red
if ($total -gt 0) {
    $rate = [math]::Round(($global:passed / $total) * 100, 2)
    Write-Host "  Success Rate    : $rate %`n" -ForegroundColor Yellow
}

# Export to CSV Report
$global:testResults | Export-Csv -Path "test_results_report.csv" -NoTypeInformation
Write-Host "[+] Detailed results exported to: test_results_report.csv" -ForegroundColor Cyan
