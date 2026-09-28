param(
    [string]$TargetHost = "localhost",
    [int]$TargetPort = 8080,
    [int]$Threads = 10,
    [double]$WithdrawAmount = 100.00,
    [double]$InitialDeposit = 500.00,
    [string]$AccountId = "",
    [string]$JwtToken = "",
    [switch]$OpenGui,
    [switch]$GenerateReport
)

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " FC-30: JMeter Thread Group - Concurrent Debit Requests Test        " -ForegroundColor Cyan
Write-Host " Acceptance Criteria: Parallel withdrawals against single account    " -ForegroundColor Cyan
Write-Host "                     to test concurrency locks (Zero Lost Updates)   " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Locate JMeter Executable
$jmeterCmd = "jmeter"
$jmeterInstalled = Get-Command $jmeterCmd -ErrorAction SilentlyContinue

if (-not $jmeterInstalled) {
    $searchPaths = @(
        "C:\Users\$env:USERNAME\Downloads\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat",
        "C:\2. TechStart Installer\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat",
        "C:\apache-jmeter*\bin\jmeter.bat",
        "C:\Program Files\apache-jmeter*\bin\jmeter.bat"
    )

    foreach ($path in $searchPaths) {
        $found = Get-Item $path -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $jmeterCmd = $found.FullName
            break
        }
    }
}

if (-not (Test-Path $jmeterCmd) -and -not $jmeterInstalled) {
    Write-Host "[!] Apache JMeter not found in standard paths or PATH." -ForegroundColor Red
    Write-Host "Please install Apache JMeter 5.6.3 or add it to PATH." -ForegroundColor Yellow
    exit 1
}

Write-Host "[+] Using JMeter binary: $jmeterCmd" -ForegroundColor Green

$jmxFile = "$PSScriptRoot\fc30-concurrent-debit-lock-test.jmx"
$resultFile = "$PSScriptRoot\fc30-results.jtl"
$logFile = "$PSScriptRoot\fc30-jmeter.log"
$reportDir = "$PSScriptRoot\fc30-html-report"

if (-not (Test-Path $jmxFile)) {
    Write-Host "[!] Test plan $jmxFile not found!" -ForegroundColor Red
    exit 1
}

# 2. If GUI mode requested
if ($OpenGui) {
    Write-Host "[+] Launching JMeter GUI with FC-30 Test Plan..." -ForegroundColor Cyan
    Start-Process -FilePath $jmeterCmd -ArgumentList "-t", "`"$jmxFile`""
    exit 0
}

# 3. Clean up previous test artifacts
if (Test-Path $resultFile) { Remove-Item $resultFile -Force }
if (Test-Path $logFile) { Remove-Item $logFile -Force }
if (Test-Path $reportDir) { Remove-Item $reportDir -Recurse -Force }

# 4. Construct CLI Execution Command
$cliArgs = @(
    "-n",
    "-t", $jmxFile,
    "-Jhost=$TargetHost",
    "-Jport=$TargetPort",
    "-Jthreads=$Threads",
    "-JwithdrawAmount=$WithdrawAmount",
    "-JinitialDeposit=$InitialDeposit",
    "-l", $resultFile,
    "-j", $logFile
)

if ($AccountId -ne "") {
    $cliArgs += "-JaccountId=$AccountId"
}
if ($JwtToken -ne "") {
    $cliArgs += "-JjwtToken=$JwtToken"
}

if ($GenerateReport) {
    $cliArgs += "-e"
    $cliArgs += "-o"
    $cliArgs += $reportDir
}

$argDisplay = $cliArgs -join " "
Write-Host "[+] Executing: $jmeterCmd $argDisplay" -ForegroundColor Yellow
Write-Host "[*] Simulating $Threads simultaneous debits against single account via Rendezvous Synchronizing Timer..." -ForegroundColor DarkYellow

$startTime = Get-Date
& $jmeterCmd $cliArgs
$elapsedMs = [math]::Round(((Get-Date) - $startTime).TotalMilliseconds, 0)

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " JMETER CONCURRENCY RUN COMPLETED ($elapsedMs ms)" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 5. Parse and Display Outcomes from Log
if (Test-Path $logFile) {
    $logContent = Get-Content $logFile
    $summaryLines = $logContent | Where-Object { $_ -match "FC-30 CONCURRENCY VERIFICATION SUMMARY|Total Concurrent Threads|Successful Debits|Rejected Overdrafts|System Errors|Initial Deposit|Total Withdrawn|Expected Balance|Actual Final Balance|FC-30 ACCEPTANCE CRITERIA VERIFIED|Zero Lost Updates" }
    
    if ($summaryLines) {
        foreach ($line in $summaryLines) {
            $prefixIdx = $line.IndexOf("INFO")
            $display = if ($prefixIdx -ge 0) { $line.Substring($prefixIdx + 5) } else { $line }
            if ($display -match "VERIFIED|Successful|Expected|Actual") {
                Write-Host $display -ForegroundColor Green
            } elseif ($display -match "Rejected") {
                Write-Host $display -ForegroundColor Yellow
            } else {
                Write-Host $display -ForegroundColor White
            }
        }
    }
}

if (Test-Path $resultFile) {
    Write-Host ""
    Write-Host "[+] Detailed CSV metrics written to: $resultFile" -ForegroundColor Cyan
}

if ($GenerateReport -and (Test-Path $reportDir)) {
    Write-Host "[+] HTML Dashboard Report generated at: $reportDir\index.html" -ForegroundColor Green
    Start-Process "$reportDir\index.html"
}
