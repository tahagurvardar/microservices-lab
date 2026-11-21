$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$base = 'http://127.0.0.1:8080/api/lab'
Write-Host 'Publishing 10 customers x 3 KEYED events...'
$k = Invoke-RestMethod -Method Post -Uri "$base/orders/keyed"
Start-Sleep -Seconds 4
$kr = Invoke-RestMethod -Uri "$base/orders/results/$($k.runId)"
Write-Host 'Keyed run:' $k.runId
$kr | ConvertTo-Json -Depth 6
Write-Host 'Publishing 10 customers x 3 UNKEYED RoundRobin events...'
$u = Invoke-RestMethod -Method Post -Uri "$base/orders/unkeyed"
Start-Sleep -Seconds 6
$ur = Invoke-RestMethod -Uri "$base/orders/results/$($u.runId)"
Write-Host 'Unkeyed run:' $u.runId
$ur | ConvertTo-Json -Depth 6
Write-Host 'Inspect partition/offset/key/seq in logs:'
docker compose logs --since 5m order-service | Select-String 'ORDER_CONSUMED|ORDER_OUT_OF_ORDER' | Select-Object -Last 60
