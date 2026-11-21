# Evidence Index - Weeks 11-12

All evidence consists of real experiment output transcribed from PowerShell. Text transcripts are **not OS screenshots**.

| Item | File | Verified finding |
|---|---|---|
| Task 1 (canonical) | `01_task1_offsets_clean.txt` | 9 keyed records, partition 1, offsets 48-56 |
| Task 2 | `02_task2_ordering.txt` | keyed 0/10 out-of-order; unkeyed 10/10 out-of-order |
| Task 3 | `03_task3_crash_and_idempotency.txt` | RAW 2 rows; SAFE 1 row; crash replay confirmed |
| Task 4 and 5 | `04_05_timeout_retry_idempotency.txt` | baseline 10.34s; timeout 2.06s; retry backoff and 1 payment row |
| Task 6 | `06_circuit_breaker.txt` | CLOSED->OPEN->HALF_OPEN->OPEN->HALF_OPEN->CLOSED |
| Unit tests | `07_unit_tests_verified.txt` | Order 7/7 and Payment 3/3, BUILD SUCCESS |

The original Task 1 transcript and temporary `01_task1_offsets_verified.txt` may contain failed preliminary shell commands; use `01_task1_offsets_clean.txt` as the canonical proof.

Report: `reports/Lab_Week11_12_Taha_Gurvardar_Report.pdf` (4 pages). The PDF contains verified transcript excerpts; it does **not** contain genuine desktop screenshots. If screenshots are mandatory, capture original screen output and insert it before submission.
