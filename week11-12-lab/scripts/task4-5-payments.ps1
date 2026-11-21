$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$base = 'http://127.0.0.1:8080/api/lab'
Write-Host 'Task4 baseline (10-second delay, no HTTP timeout):'
Invoke-RestMethod -Method Post -Uri "$base/payments/baseline?delayMs=10000" | Format-List
Write-Host 'Task4 bounded timeout (connect=1s/read=2s), fallback payment pending:'
Invoke-RestMethod -Method Post -Uri "$base/payments/timeout?delayMs=10000" | Format-List
Write-Host 'Task5: 20 requests, each has a distinct order ID and 50% simulated 503 rate:'
1..20 | ForEach-Object {
    $id = [guid]::NewGuid().ToString()
    Invoke-RestMethod -Method Post -Uri "$base/payments/retry?failRate=50&orderId=$id" |
        Select-Object orderId, elapsedMs, result
}
Write-Host 'Task5: Bad Request 400 must be ignored by retry:'
$bad = [guid]::NewGuid().ToString()
Invoke-RestMethod -Method Post -Uri "$base/payments/retry?failRate=101&orderId=$bad" | Format-List
Write-Host "Check PAYMENT_ATTEMPT for orderId=$bad; should be exactly one attempt."
Write-Host 'Task5: 3000 ms processing with 2000 ms timeout, repeated same key:'
$id = [guid]::NewGuid().ToString()
Invoke-RestMethod -Method Post -Uri "$base/payments/retry?delayMs=3000&orderId=$id" | Format-List
Start-Sleep -Seconds 4
Invoke-RestMethod -Method Post -Uri "$base/payments/retry?delayMs=0&orderId=$id" | Format-List
Invoke-RestMethod -Uri "http://127.0.0.1:18081/payments/count?orderId=$id" | Format-List
Write-Host "Expected payment row count=1 for orderId=$id"
docker compose logs --since 15m order-service | Select-String 'PAYMENT_ATTEMPT|PAYMENT_HTTP_ERROR' | Select-Object -Last 70
docker compose logs --since 15m payment-service | Select-String 'PAYMENT_CREATED|PAYMENT_REUSED|PAYMENT_COMPLETED' | Select-Object -Last 35
