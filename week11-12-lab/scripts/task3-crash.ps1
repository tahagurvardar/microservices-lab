# Task 3: deterministic crash-before-ack and replay verification.
# WARNING: intentionally halts ONLY the Week 11-12 lab order-service JVM.
# Compatible with Windows PowerShell 5.1.
$ErrorActionPreference = 'Stop'
$approval = Read-Host 'Task3 will intentionally HALT the lab order-service twice. Type YES to continue'
if ($approval -cne 'YES') { Write-Host 'Cancelled.'; return }
Set-Location (Join-Path $PSScriptRoot '..')
$base = 'http://127.0.0.1:8080/api/lab'
$containerId = (docker compose ps -q order-service).Trim()
if (-not $containerId) { throw 'Lab order-service container not found. Run docker compose up -d --no-build first.' }

foreach ($mode in @('raw', 'safe')) {
    $id = [guid]::NewGuid().ToString()
    Write-Host "`n--- Task3 ${mode}: eventId=$id ---"
    $published = Invoke-RestMethod -Method Post -Uri "$base/crash/send/$mode" `
        -ContentType 'application/json' -Body (ConvertTo-Json @{ eventId = $id; confirmCrash = $true })
    if ($published.eventId -ne $id) { throw 'Unexpected publish result.' }
    Write-Host "Published crash-armed Kafka event $id. Waiting for actual container exit..."

    $exited = $false
    for ($i = 0; $i -lt 40; $i++) {
        $status = (docker inspect --format '{{.State.Status}}' $containerId).Trim()
        if ($status -eq 'exited') { $exited = $true; break }
        Start-Sleep -Milliseconds 500
    }
    docker compose ps -a order-service
    if (-not $exited) {
        docker compose logs --no-color --tail 50 order-service
        throw "Expected intentional crash did NOT occur for mode=$mode. Do not use this as Task 3 evidence."
    }
    Write-Host "Verified: order-service EXITED after DB commit, before Kafka ack ($mode)."
    Write-Host 'Starting only the intentionally halted lab order-service...'
    docker compose start order-service

    $verified = $false
    $result = $null
    for ($i = 0; $i -lt 50; $i++) {
        Start-Sleep -Seconds 1
        try {
            $result = Invoke-RestMethod -Uri "$base/crash/results/$id"
            $logLines = @(docker compose logs --no-color --since 5m order-service 2>&1)
            $ackSeen = @($logLines | Select-String -SimpleMatch "CRASH_EVENT_ACK mode=$mode eventId=$id").Count -gt 0
            $rowsRight = if ($mode -eq 'raw') {
                $result.rawRows -eq 2
            } else {
                $result.safeRows -eq 1 -and $result.processedEventRows -eq 1
            }
            $replayObserved = if ($mode -eq 'safe') {
                @($logLines | Select-String -SimpleMatch "CRASH_EVENT_PROCESSED mode=safe eventId=$id" |
                    Select-String -SimpleMatch 'duplicate=true').Count -gt 0
            } else {
                $result.rawRows -eq 2
            }
            if ($ackSeen -and $rowsRight -and $replayObserved) {
                $verified = $true
                break
            }
        } catch {
            # The application may still be booting. Retry up to the bound above.
        }
    }
    Write-Host "Database result for $mode :"
    if ($null -ne $result) { $result | Format-List }
    Write-Host 'Proof logs:'
    docker compose logs --no-color --since 5m order-service |
        Select-String "eventId=$id" | Select-String 'CRASH_EVENT_RECEIVED|LAB_INTENTIONAL_HALT|CRASH_EVENT_PROCESSED|CRASH_EVENT_ACK' |
        Select-Object -Last 12
    if (-not $verified) {
        throw "Task3 verification FAILED for mode=$mode; examine logs and DB."
    }
    Write-Host "TASK3 PASS ($mode): intentional exit + Kafka replay + expected DB row counts verified."
}
Write-Host "`nTASK3 PASS: RAW generated a duplicate; SAFE suppressed the duplicate."
