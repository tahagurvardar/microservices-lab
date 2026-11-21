# Task 1 (Kafka offsets): run from Windows PowerShell 5.1 after Docker starts.
# Exact assignment keys A, B and C. The producer runs inside the Kafka
# container so that PowerShell's native stdin handling cannot add a UTF-8 BOM.
# Safe to rerun: records are read from the partition's current end offset.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

# Kafka 3.9 default Murmur2 hashes the three ASCII keys A, B, C to
# partition 1 when the topic has 3 partitions. A BOM before A would be a
# different key, so do not pipe Windows PowerShell text into the producer.
$offsetOutput = @(docker compose exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh `
    --bootstrap-server localhost:9092 --topic orders --time -1)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to obtain the current Kafka offsets.'
}
$partitionLine = @($offsetOutput | Where-Object { $_ -match '^orders:1:[0-9]+\s*$' } | Select-Object -First 1)
if ($partitionLine.Count -ne 1) {
    throw ('Could not find the current offset of orders partition 1: ' + ($offsetOutput -join ', '))
}
$startOffset = [long](($partitionLine[0] -split ':')[2].Trim())
Write-Host "Current orders partition 1 end offset: $startOffset"

# Let sh/printf generate the raw bytes entirely inside Linux (no BOM).
$produce = @'
printf 'A:1\nB:1\nC:1\nA:2\nB:2\nC:2\nA:3\nB:3\nC:3\n' | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders --property parse.key=true --property key.separator=:
'@
Write-Host 'Sending 9 clean UTF-8 keyed messages (A/B/C x 3)...'
docker compose exec -T kafka sh -c $produce
if ($LASTEXITCODE -ne 0) {
    throw 'The Kafka producer failed.'
}

Write-Host "Reading exactly these 9 new records from partition 1, offset $startOffset..."
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server localhost:9092 --topic orders --partition 1 --offset $startOffset `
    --max-messages 9 --timeout-ms 20000 `
    --property print.key=true --property print.partition=true --property print.offset=true
if ($LASTEXITCODE -ne 0) {
    throw 'The Kafka consumer failed or timed out before reading all 9 messages.'
}
Write-Host 'Task 1 result: all nine messages consumed; inspect the key and offset values above.'
