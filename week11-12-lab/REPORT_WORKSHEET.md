# Weeks 11–12 lab report — worksheet (NOT the final submitted PDF)

**Student:** [fill in]  **Repository:** https://github.com/tahagurvardar/microservices-lab

## Task 1 — Kafka offsets (10)
Paste the real console output (key, partition, offset) and fill:

| Key | Partition | Observed offsets |
| --- | --- | --- |
| A | [measure] | [measure] |
| B | [measure] | [measure] |
| C | [measure] | [measure] |

Explanation (one paragraph): [observed vs expected, offsets are partition-local]

## Task 2 — Keyed / unkeyed event order (15)
Keyed run ID: [fill]   Out-of-order keyed customers: [measure]
Unkeyed run ID: [fill] Out-of-order unkeyed customers: [measure]
Customer with reordered seq: [customer, observed seq, partitions]
Logs screenshot: [insert]. Explanation: [partition-only ordering, RoundRobinPartitioner, deliberate CREATED processing delay]

## Task 3 — Crash + idempotency (20)
Raw event ID: [fill]  Raw rows before restart: [measure]  Raw rows after restart: [measure]
Safe event ID: [fill] Safe rows after restart: [measure]
Log evidence: same partition and offset processed again; screenshot: [insert]
Explanation: [database commit before Kafka ack, at-least-once replay, unique processed_event key]

## Task 4 — Timeout (10)
No-timeout elapsedMs: [measure]  With timeout elapsedMs: [measure]
Fallback text: [copy] Screenshots: [insert]
Explanation: [connect 1s, read 2s; remote may complete even after caller times out]

## Task 5 — Retry + idempotency key (15)
20 requests results: [count successes, failures; measured]
Retry log examples with timestamps: [insert]
400 test attempt count: [measure]
3000ms delay same key payment count: [measure]
Explanation: [max 3, 500/1000ms waits, 400 ignored, unique payment key]

## Task 6 — Circuit breaker (20)
30-request timeline: [screenshots / durations]
Actuator transitions: [CLOSED_TO_OPEN → OPEN_TO_HALF_OPEN → HALF_OPEN_TO_CLOSED]
OPEN median elapsedMs: [measure] Task4 timeout elapsedMs: [measure]
Explanation: [10 requests sliding window, >=50% failures, 10s OPEN, 3 half-open]

## Conclusion
[One paragraph based on *measured* outcomes]

Submission: export **3–5 pages** to `Lab_Week11_12_Surname_Name.pdf` using actual screenshots and measured numbers. Do not submit this worksheet itself.
