# WARNING: deliberately stops the lab payment-service, not the earlier Task 1–6 stack.
$ErrorActionPreference = 'Stop'
$approval = Read-Host 'Task6 will STOP the lab payment-service temporarily. Type YES to continue'
if ($approval -cne 'YES') { Write-Host 'Cancelled.'; return }
Set-Location (Join-Path $PSScriptRoot '..')
$base = 'http://127.0.0.1:8080'
Write-Host 'Stop payment-service to simulate an outage.'
docker compose stop payment-service
Write-Host '30 requests at ~1 per second, record elapsedMs while OPEN.'
1..30 | ForEach-Object {
    $id = [guid]::NewGuid().ToString()
    $r = Invoke-RestMethod -Method Post -Uri "$base/api/lab/payments/circuit?orderId=$id"
    Write-Host "request=$_ durationMs=$($r.elapsedMs) result=$($r.result)"
    Start-Sleep -Seconds 1
}
Write-Host 'Restart payment-service; wait for HTTP readiness.'
docker compose start payment-service
Start-Sleep -Seconds 12
Write-Host 'Send 3+ successes; HALF_OPEN probes should close the circuit.'
1..5 | ForEach-Object {
    $id = [guid]::NewGuid().ToString()
    Invoke-RestMethod -Method Post -Uri "$base/api/lab/payments/circuit?orderId=$id" |
        Select-Object mode, elapsedMs, result
    Start-Sleep -Seconds 1
}
Write-Host 'Circuit breaker state transitions from actuator:'
Invoke-RestMethod -Uri "$base/actuator/circuitbreakerevents" | ConvertTo-Json -Depth 12
Write-Host 'Inspect for CLOSED_TO_OPEN, OPEN_TO_HALF_OPEN, HALF_OPEN_TO_CLOSED.'
