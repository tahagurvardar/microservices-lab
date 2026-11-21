# Weeks 11–12: Kafka Event Ordering + Resilience4j Fault Tolerance

Independent lab solution for the 100-point PDF **Lab: Event Ordering with Kafka and Fault Tolerance with Resilience4j** (November 19, 2026). This folder is independent of Tasks 1–6; do **not** replace the repository's root Compose file. Java **21**, Spring Boot **3.3.5**, Spring Kafka, Resilience4j **2.2.0**, Kafka **3.9.1**, PostgreSQL **16**.

## Architecture

- `order-service` (port **8080**): Kafka producer and three-thread consumer; saves JSON events to PostgreSQL before manual Kafka acknowledgments; calls payment-service via HTTP; includes timeout, `@Retry`, `@CircuitBreaker` and fallback endpoints.
- `payment-service` (port **8081 inside Docker**, **18081 on the Windows host**): `POST /pay?delayMs=...&failRate=...`; `Idempotency-Key` header; PostgreSQL unique-key enforcement, including overlapping requests.
- Kafka topic **`orders` with 3 partitions**, plus one-partition `orders-crash-raw` and `orders-crash-safe` for isolated crash tests.
- One PostgreSQL database with `lab_event_log`, `processed_event`, `crash_marker`, `lab_payment` tables.

**Port separation:** The earlier Task 1–6 stack already uses host 8081 and 5432. This lab maps payment's internal 8081 to **host 18081**, and PostgreSQL's internal 5432 to **host 15432**. Kafka uses host **9092**, while the earlier stack exposes Kafka on 9094. This prevents conflict. The two Spring apps still listen on their teacher-required internal ports 8080 and 8081.

These are **demo-only** credentials (`lab` / `labpass`), not real application secrets. Do not expose this lab's services publicly.

## Start (Windows PowerShell 5.1)

1. Docker Desktop: Linux containers, running.
2. From repository root: `cd D:\dev\microservices-lab\week11-12-lab`.
3. `docker compose config --quiet`
4. `docker compose up -d --build`
5. `docker compose ps` (Kafka and PostgreSQL should be healthy; services running).
6. `Test-NetConnection 127.0.0.1 -Port 8080`
7. `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders` (expect **3** partitions).

The first build may download the Java 21 and Maven images. PostgreSQL initializes its tables from both services' `schema.sql` (safe to re-run).

Stop safely with `docker compose stop` or `docker compose down` (without `-v`). **Never use `down -v` during this lab**; that would delete DB/Kafka evidence stored in volumes.

## Unit tests (without installing Maven on Windows)

Run in `week11-12-lab`:

```powershell
# order-service
$root = (Get-Location).Path
docker run --rm -v "${root}:/work" -v week1112-maven-cache:/root/.m2 -w /work/order-service maven:3.9-eclipse-temurin-21 mvn -B test
# payment-service
docker run --rm -v "${root}:/work" -v week1112-maven-cache:/root/.m2 -w /work/payment-service maven:3.9-eclipse-temurin-21 mvn -B test
```

## Task 1 — Console offsets (10)

Run **before Task 2**, while `orders` topic is fresh. Nine keyed plain-text messages are accepted by the application listener (it logs and acknowledges non-JSON input). Execute:

```powershell
.\scripts\task1-offsets.ps1
```

The output shows key, partition and offset. Make a **real** table `A/B/C → partition → offsets` in `REPORT_WORKSHEET.md`. A message offset is unique within its partition, not a global total. To manually re-create the required topic if missing:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic orders --partitions 3 --replication-factor 1
```

On a second execution the `--from-beginning --max-messages 9` consumer reads the **oldest** nine records, not necessarily the new ones. Do not reset volumes to hide results.

## Task 2 — Ordering by key vs. RoundRobinPartitioner (15)

```powershell
.\scripts\task2-ordering.ps1
```

Or manually run `POST http://127.0.0.1:8080/api/lab/orders/keyed` and then `.../orders/unkeyed` with no body. Each publishes 10 customers × (CREATED, PAID, SHIPPED), seq 1–3. Read `GET /api/lab/orders/results/{runId}`; each listener logs partition, offset, key, customer and seq. Keyed case sends Kafka key `customerId` (partition-local order), unkeyed case sends null keys using **`org.apache.kafka.clients.producer.RoundRobinPartitioner`**. The consumer has **concurrency = 3**.

**Demonstration detail:** On purpose, the unkeyed consumer sleeps 900 ms when processing `CREATED` to make a cross-partition processing reordering easy to observe; this is explicitly logged and is not a claim that Kafka always reorders keyless events. A keyed customer stays on one partition, so delay does not violate its order. Log `ORDER_OUT_OF_ORDER` and a customer sequence such as `[2,3,1]` are the evidence.

## Task 3 — Crash, offset replay, idempotent save (20)

**WARNING:** This deliberately executes `Runtime.getRuntime().halt(1)` in the **order-service lab container**, after PostgreSQL transaction commits but **before** Kafka `acknowledge()`. The script requires typing `YES`, and the API requires JSON `confirmCrash: true`. It will temporarily stop only the lab order-service; payment-service and Tasks 1–6 are unaffected.

```powershell
.\scripts\task3-crash.ps1
```

Manual sequence for each `raw`, `safe`:
1. Generate a UUID and `POST /api/lab/crash/send/{mode}` with JSON `{"eventId":"...uuid...","confirmCrash":true}`. No separate arm call is required.
2. The order-service logs `LAB_INTENTIONAL_HALT` and stops, after the DB row is committed but before Kafka ack.
3. `docker compose start order-service` (no `-v` / no rebuild).
4. `GET /api/lab/crash/results/{eventId}`. For `raw`, expect **rawRows=2** (same partition/offset processed twice). For `safe`, expect **safeRows=1**, **processedEventRows=1**. `processed_event(event_id PRIMARY KEY)` and the business row are committed in the *same DB transaction*.

A durable `crash_marker` prevents endless re-crashes. Spring Kafka configuration explicitly sets `enable-auto-commit=false` and `ack-mode=manual`.

## Task 4 — Timeout (10)

```powershell
Invoke-RestMethod -Method Post 'http://127.0.0.1:8080/api/lab/payments/baseline?delayMs=10000'
Invoke-RestMethod -Method Post 'http://127.0.0.1:8080/api/lab/payments/timeout?delayMs=10000'
```

Compare the returned `elapsedMs`. Baseline uses no read timeout; timed client uses **connect 1000 ms**, **read 2000 ms**. On timeout, order-service answers `payment pending (...)`. The payment-service can still finish server-side after the caller has timed out, illustrating why idempotent retries matter.

## Task 5 — Retry and idempotency key (15)

Run `.\scripts\task4-5-payments.ps1` for tasks 4 and 5 together. Config: `@Retry(name="paymentRetry")`, `max-attempts=3`, `wait-duration=500ms`, exponential multiplier **2** (retry waits 500 ms and 1000 ms); HTTP **400 is ignored**, so log has just one attempt. A 50% simulated 503 occurs **before** a payment is claimed. Every HTTP attempt logs UTC timestamp and orderId.

The header `Idempotency-Key` is set to the `orderId`. Payment-service uses `lab_payment.idempotency_key` as its **PRIMARY KEY**, which prevents double payment, including a concurrent second request while an initial `delayMs=3000` call is still processing. A duplicate returns stored COMPLETED or PROCESSING (HTTP 202) state. To demonstrate:

```powershell
$id = [guid]::NewGuid().ToString()
Invoke-RestMethod -Method Post "http://127.0.0.1:8080/api/lab/payments/retry?delayMs=3000&orderId=$id"
Start-Sleep -Seconds 4
Invoke-RestMethod -Uri "http://127.0.0.1:18081/payments/count?orderId=$id"
```

Expected payment count: **1**. This is a *payment simulation*, not a real charge.

## Task 6 — Circuit breaker (20)

Run `.\scripts\task6-circuit.ps1`. It stops only the lab payment-service and sends **30 requests**, one per second. Settings: `@CircuitBreaker`, size **10**, threshold **50%**, OPEN wait **10s**, HALF_OPEN permitted **3**. Requests while OPEN immediately return `payment pending (circuit fallback: CallNotPermittedException)` without waiting for a network timeout. The script restarts the payment-service and sends successful probes.

Evidence endpoint:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/actuator/circuitbreakerevents' | ConvertTo-Json -Depth 12
```

Look for `CLOSED_TO_OPEN`, `OPEN_TO_HALF_OPEN`, `HALF_OPEN_TO_CLOSED` in state transition events. If HALF_OPEN occurred while the payment-service was still stopped, the circuit might return OPEN again; after restart and a fresh 10-second wait, repeat healthy probes. Record actual elapsed times and compare them with Task 4.

## What to submit

Teacher requires **both**:

1. Git repository link containing this `docker-compose.yml`, `order-service`, `payment-service`, and this README.
2. A **3–5 page PDF report** `Lab_Week11_12_Surname_Name.pdf` with actual screenshots, log evidence, measured times, offset table and one paragraph per task. Use `REPORT_WORKSHEET.md` for notes and `evidence/` for your screenshots. **Do not invent screenshots or measurements.**

**Compatibility:** Tasks 1–6 remain untouched at repository root. The lab can run alongside them, because ports and Compose project/volumes are isolated.
