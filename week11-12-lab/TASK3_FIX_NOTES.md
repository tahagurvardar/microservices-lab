# Task 3 — conflict (HTTP 409) hotfix

The prior patch added an in-memory one-shot arm to the `/crash/send/{mode}`
endpoint; on the reported run that state was unavailable and the endpoint
returned `409 Conflict` before publishing any Kafka record. This version
uses **stateless explicit confirmation** in the request JSON instead.

The script still requires the operator to type **YES**. It then sends:
`{ "eventId": "<new UUID>", "confirmCrash": true }` to
`POST /api/lab/crash/send/{mode}`. The event contains `runId=crash-armed-{mode}`
and intentionally halts the isolated lab `order-service` JVM **once** after
its DB transaction, before Kafka ack. A durable crash marker prevents another
halt on replay.

## Install / run (PowerShell 5.1)

Extract ZIP from Downloads into `D:\dev\microservices-lab` using
`Expand-Archive -Force`, then:

```powershell
cd D:\dev\microservices-lab\week11-12-lab
docker compose up -d --build order-service
.\scripts\task3-crash.ps1
```

Do not delete volumes or database records. Expected rawRows=2; safeRows=1;
processedEventRows=1; `LAB_INTENTIONAL_HALT` in logs for both modes, and
`duplicate=true` on SAFE replay. The script fails loudly otherwise.
