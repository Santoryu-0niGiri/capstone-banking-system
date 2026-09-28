param(
    [string]$TargetHost = "localhost",
    [int]$TargetPort = 8080,
    [int]$Threads = 50,
    [int]$RampUp = 5,
    [int]$LoopCount = 1000,
    [string]$AccountId = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    [string]$JwtToken = ""
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " FC-41: JMeter Refactored Path Concurrency Benchmark Test " -ForegroundColor Cyan
Write-Host " Target SLA: >= 800 TPS, <= 50ms p95 latency              " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$jmxFile = "$PSScriptRoot\debit-concurrency-baseline.jmx"
$resultFile = "$PSScriptRoot\results.jtl"
$reportDir = "$PSScriptRoot\html-report"

if (Test-Path $resultFile) { Remove-Item $resultFile -Force }
if (Test-Path $reportDir) { Remove-Item $reportDir -Recurse -Force }

$cmd = "jmeter -n -t `"$jmxFile`" -Jhost=$TargetHost -Jport=$TargetPort -Jthreads=$Threads -JrampUp=$RampUp -JloopCount=$LoopCount -JaccountId=$AccountId -JjwtToken=$JwtToken -l `"$resultFile`" -e -o `"$reportDir`""

Write-Host "Executing: $cmd" -ForegroundColor Yellow
Invoke-Expression $cmd

Write-Host "Benchmark complete. Results saved in $reportDir" -ForegroundColor Green

