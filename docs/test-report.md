# Test Report

> Updated at every milestone. Every result here comes from a command that was actually run.

Exact commands, test counts and results per milestone.

## Milestone M1 — Phase 1 skeleton (2026-11-02)

| Command | Result |
|---|---|
| `backend: ./mvnw -B verify` | BUILD SUCCESS — `HealthControllerTest` 3/3, `ApplicationIT` 3/3 (Testcontainers PostgreSQL 18) |
| `frontend: npm run typecheck` | exit 0 |
| `frontend: npm run lint` | exit 0 |
| `frontend: npm run build` | built |
| `frontend: npm test` | 4/4 passed (`BackendStatus.test.tsx`) |

## Milestone M2 — Phase 2 algorithm engine (2026-11-02)

| Command | Result |
|---|---|
| `backend: ./mvnw -B verify` | BUILD SUCCESS. Surefire 95/95 (92 algorithm tests + `HealthControllerTest` 3); Failsafe `ApplicationIT` 3/3 |
| `backend: ./mvnw -q compile exec:java -Dexec.mainClass=com.chargewise.algorithm.AlgorithmSampleRunner` | Prints all five worked examples; percentiles for seed 20261102 match the full sort |

Frontend sources did not change in Phase 2; the M1 frontend results still apply.

Per-algorithm test lists and oracle evidence: [algorithms.md](algorithms.md).

## Milestone M3 — Phase 3 domain and persistence (2026-11-02)

| Command | Result |
|---|---|
| `backend: ./mvnw -B verify` | BUILD SUCCESS. Surefire 100/100 (adds `PowerValidationTest` 5). Failsafe 20/20: `ApplicationIT` 3, `SchemaConstraintsIT` 8, `RepositoryOwnershipIT` 5, `DemoDataSeederIT` 4 |
| `python scripts/generate_dataset.py --seed 20261102 --out datasets` (twice) | 12 stations, 55 connectors, 40 vehicles, 120 requests; identical SHA-256 on both runs |
| `java -jar target/chargewise-backend-0.0.1-SNAPSHOT.jar` with `CHARGEWISE_DEMO_SEED_ON_STARTUP=true` | Health `UP`/`UP`; `SeedSummary[users=21, stations=12, connectors=55, vehicles=40, bookings=120]`; every booking window starts in the future |
| `frontend: npm run typecheck` / `lint` / `build` / `test` | exit 0 / exit 0 / built / 4/4 passed |

## Milestone M4 — Phase 4 core API and allocation pipeline (2026-11-02)

| Command | Result |
|---|---|
| `backend: ./mvnw -B verify` | BUILD SUCCESS. Surefire 130/130 (adds `GlobalExceptionHandlerTest` 7, `JwtConfigTest` 3, `CompatibilityGraphBuilderTest` 13, `PowerProblemBuilderTest` 5, `SessionLifecycleTest` 2). Failsafe 67/67: `AuthSecurityIT` 10, `StationVehicleIT` 8, `BookingIT` 6, `AllocationPipelineIT` 11, `AlgorithmLabIT` 7, `SessionAndDashboardIT` 5, plus the M3 suites |
| `frontend: npm run typecheck` / `lint` / `build` / `test` | exit 0 / exit 0 / built / 4/4 passed |
| HTTP smoke test: packaged jar on port 8089 against Compose Postgres, demo data seeded | 25/25 checks passed: health, auth, stations, nearby, availability, vehicle, booking (and a zone-less 400), allocation run and fetch, booking, session history, dashboard, operator lists, all four lab endpoints, 401/403, `/v3/api-docs`. `GET /api/v1/health`: `UP`, database `UP` |

**Demo allocation run (120 synthetic requests and 1 smoke-test request):**

- 121 eligible.
- 40 assigned, all fully served; every bottleneck was `SESSION_DEMAND`.
- 60 waitlisted: 45 `LOST_CONTENTION`, 13 `STATION_NOT_OPERATIONAL`, 2 `CONNECTORS_OUT_OF_SERVICE`.
- 21 rejected: 14 `NO_COMPATIBLE_CONNECTOR_TYPE`, 7 `INSUFFICIENT_CONNECTOR_POWER`.
- Requested-power percentiles: p50 6.88 kW, p75 13.00 kW, p90 18.00 kW.
- 0 capacity violations.

These are measured values from one run on synthetic data, not a benchmark.

**Review Focus pins:**

- RF3: `bookingWithoutZoneOrOffSlotIsRejected`.
- RF5: `concurrentAllocationRunsNeverDoubleBookAConnector`, with four parallel runs, two connectors and six requests.
- Partial persistence: `persistenceFailureLeavesNoPartialAllocation`. A spy fails the third session save, and no
  booking, session, power row or run survives.

## Milestone M5 — Phase 5 driver portal and operator console (2026-11-03)

| Command | Result |
|---|---|
| `frontend: npm run typecheck` | exit 0 |
| `frontend: npm run lint` (`oxlint --deny-warnings`) | exit 0 |
| `frontend: npm test` (vitest) | 13 files, 49/49 passed |
| `frontend: npm run build` | built; largest chunk 491 kB |

**Test files:**

- `tokenStore` 4, `api` 5 and `RequireRole` 3: session storage, the bearer token, the 401 hook and role routing.
- `AuthPages` 2: a new driver lands in the garage, and sign-in returns to the page the user came from. This was found
  in the browser, and both tests failed before the fix.
- `reasons` 5, `format` 2, `BookingForm` 6, `SessionProgress` 2, `BackendStatus` 4.
- `AllocationOutcome` 7: the verbatim objective statement, the reason filter, binding stations marked and listed first,
  the "no limit binds" state, the reason at a station with no match, and scrolling to the ledger.
- `labInstances` 5 and `AlgorithmLab` 2: seeded instance generators and the lab tabs.
- `PercentileLedger` 2.

**Live checks:** run against the packaged backend on port 8080 with demo data, in Chrome and headless Chrome. These
covered driver registration, adding a vehicle (registration normalised), requesting a charging window, allocation run
#2 (37 assigned, 46 waitlisted, 32 rejected of 115 eligible), all four lab tabs (matching 2 vs 1, CLRS flow 23 equals
the min cut, Quickselect agrees with sorting, segment tree agrees with the naive scan), the analytics range explorer
and stations admin. At 390 px no page scrolls horizontally; wide tables scroll inside their panel.


## Milestone M6 — Phase 6 simulation and benchmarks (2026-11-03)

| Command | Result |
|---|---|
| `backend: ./mvnw -B verify` | BUILD SUCCESS. Surefire 185/185 (adds `ScenarioGeneratorTest` 21, `SimulationExecutorTest` 22, `BenchmarkRunnerTest` 7, `JmhBenchmarksTest` 5; `AlgorithmPurityTest` now also walks `simulation` and `benchmark`). Failsafe 79/79 (adds `SimulationIT` 6, `BenchmarkIT` 6) |
| `frontend: npm run typecheck` / `lint` / `build` | exit 0 / exit 0 / built |
| `frontend: npm test` (vitest) | 15 files, 57/57 passed (adds `SimulationsPage` 3, `BenchmarksPage` 3, `formatNanos` 2) |
| JMH: `java -cp … org.openjdk.jmh.Main RangeQueryBenchmark -p size=SMALL -wi 1 -i 2` | ran through the documented command; harness generated for all 8 benchmark methods |

**Key tests:**

- `rerunReproducesAllNonTimingMetrics` and `SimulationIT.operatorExecutesSimulationAndRetrievesPersistedResults`:
  the same configuration and seed give the same input hash, and a re-run reports `reproducedPreviousRun: true`.
- `everyScenarioHasZeroCapacityViolations`: 8 scenarios × 5 seeds, both strategies.
- `highContentionScenarioMatchingNotWorseThanGreedy` and `sharedFeederScenarioEkAllocatesAtLeastGreedy`: the
  per-slot evidence counts are 0, and the summed per-slot gain over seeds 1–5 is positive (mutation-checked: both fail
  with greedy substituted for the algorithm).
- `BenchmarkRunnerTest`: nearest-rank statistics, every case verified and timed for the configured trials, algorithm
  quality never below its baseline, identical cases for the same seed, and every quality exact in JavaScript
  (mutation-checked).
- `BenchmarkIT`: `202` then polling to `COMPLETED`, the same seed reproducing every non-timing result, defaults,
  validation (`trials`, `sizes`, `seed`, unknown size), `404`, and `403` for drivers.
- `JmhBenchmarksTest`: the annotation processor generated the harness, and each JMH benchmark computes the same answer
  as its baseline.

**Live checks:** packaged backend on port 8080 with demo data, headless Chrome at 1440 px and 390 px. A
`HIGH_CONTENTION` simulation (seed 20261102) ran and compared; three SMALL benchmark runs with seed 20261103 gave
identical results (for example 722.09 kW against 701.23 kW allocated with shared feeders at 10 stations), with
different timings. Two layout defects found this way (the time verdict wrapping, tall rows on phones) were fixed
and re-checked.

## Milestone M7 — Phase 7 verification and submission (2026-11-03)

| Command | Result |
|---|---|
| `backend: ./mvnw -B verify` | BUILD SUCCESS. Surefire 185/185, Failsafe 79/79. The two `ERROR` log lines come from tests that deliberately trigger the unhandled-error handler and the allocation rollback |
| `frontend: npx tsc -b --noEmit` / `npm run lint` / `npx vitest run` / `vite build` | exit 0 / exit 0 / 15 files, 57/57 passed / built |
| In-application benchmarks, seed 20261103, all families, Small to Large, 3 warm-ups × 7 trials | Every case verified correct before timing, with zero capacity violations; 27.3 s. Exported to `docs/benchmark-results/all-sizes-seed-20261103.json` |
| The same for `SELECTION` and `POWER_FEEDERS`, seeds 1, 2 and 3 | Every case verified; `selection-feeders-seed-{1,2,3}.json` |
| JMH, full suite (documented command) | 64 benchmark cases, 9 min 1 s, `docs/benchmark-results/jmh-seed-20261103.json` |
| `python scripts/plot_benchmarks.py …all-sizes-seed-20261103.json --out docs/diagrams` | Five charts, inspected; direct labels separate where the lines finish close together |

**Clean-environment README walkthrough (2026-11-03).**

- **Setup.**
  - The repository was copied without `node_modules`, `target`, `.env` or Git metadata.
  - `cp .env.example .env` was followed by freshly generated values. Port 5434 was used because 5432 and 5433 were
    taken, which is the README's own troubleshooting path.
  - `docker compose up -d db` was followed by `pg_isready` → `accepting connections`.
- **Backend.** `CHARGEWISE_DEMO_SEED_ON_STARTUP=true ./mvnw spring-boot:run` → Flyway migrated the empty database
  and the seeder loaded 21 users, 12 stations, 55 connectors, 40 vehicles and 120 bookings. `/api/v1/health` →
  `UP` / `UP`.
- **Frontend.** `npm ci` (0 vulnerabilities), then `npm run dev`. The page and the `/api` proxy both answered.
- **Through the proxy:**
  - operator login `200`
  - allocation run `201`: 120 eligible, 40 assigned, 59 waitlisted, 21 rejected
  - benchmark run `202`, then `COMPLETED`
  - the README's API examples were checked on the running server: booking validation `400 VALIDATION_FAILED` on
    `requestedEnergyKwh`; Lab matching 2 against greedy's 1; Lab as a driver `403`
- **README Tests section.** `./mvnw test` 185/185; frontend typecheck, lint and build exit 0; `npm test` 57/57;
  the plot command wrote all five charts.
- **Cleanup.** The walkthrough's database, volume and processes were removed afterwards.

**Benchmark findings recorded honestly** ([benchmark-report.md](benchmark-report.md)):

- greedy matching is within one request of maximum on random graphs
- Edmonds-Karp ties greedy on the spec's tree-shaped network, as predicted
- feeder gains are input-dependent: 9 of 24 cases
- Quickselect loses to the JDK sort on presorted input
- JMH overturned the in-application result that the naive scan beats the segment tree at n = 1,000

## India-wide dataset (2026-11-04)

The station network moved from 36 OpenStreetMap stations in Mumbai and Pune to every public car charging station in
India listed by Open Charge Map or OpenStreetMap.

| Command | Result |
|---|---|
| `python scripts/generate_dataset.py --seed 20261102 --out datasets` (twice) | 1,971 stations, 4,614 connectors, 134 vehicles (100 drivers), 600 requests; identical MD5 on all four files across both runs |
| `backend: ./mvnw -q verify` | BUILD SUCCESS. Surefire 189/189, Failsafe 86/86 (adds `StationVehicleIT.stationKeepsCityAndStateAndTheDirectoryListsEveryStation`; seed counts updated in `DemoDataSeederIT` and `AuthSecurityIT`) |
| `frontend: npm run typecheck` / `lint` / `test` | exit 0 / exit 0 / 16 files, 60/60 passed (adds `places.test.ts` 3) |
| `CHARGEWISE_DEMO_SEED_ON_STARTUP=true ./mvnw spring-boot:run`, then `POST /operator/demo/reset` | `{users: 101, stations: 1971, connectors: 4614, vehicles: 134, bookings: 600}` |
| `GET /stations/all` with `Accept-Encoding: gzip` | `200`, 1,971 stations; 1,011 KB of JSON sent as 139 KB |

**Demo allocation run (600 requests), run twice from a fresh reset with the same result:**

- 600 eligible; 394 assigned, 197 waitlisted, 9 rejected.
- Waitlisted: 187 `LOST_CONTENTION`, 6 `CONNECTORS_OUT_OF_SERVICE`, 4 `STATION_NOT_OPERATIONAL`. Rejected: 9
  `NO_COMPATIBLE_CONNECTOR_TYPE`.
- Matching: 1,105 compatibility edges, cardinality 394 after 394 augmentations. Power: 4,849.87 of 4,851.44 kW
  requested granted.
- Measured timings: matching 0.05 ms, Edmonds-Karp 156 ms, Quickselect 0.44 ms; the whole run, including
  persistence, 1.15 s server-side and 1.17 s over HTTP.

## Final review fix pass (2026-11-03)

A whole-branch review found no Critical issues and five Important ones; two Minor findings were re-graded to Important. Each code fix has a test that was watched
failing first.

| Finding | Test (RED → GREEN) |
|---|---|
| Lab decimal percentiles (99.9, 0.9) took the double-rounded rank | `PercentilesTest.nearestRankIndexFromPercentIsExact`, `AlgorithmLabIT.quickselectDecimalPercentilesUseTheExactRank` |
| Station, connector and cancellation writes could interleave with an allocation run | `AllocationLockIT` (3): each request waits while the advisory lock is held, then succeeds |
| A queued request whose window started before any run covered it stayed PENDING forever | `AllocationPipelineIT.queuedRequestWhoseWindowPassedBeforeAnyRunIsRejectedAsExpired` |
| The long-augmenting-path test would also pass with a recursive matcher | `BipartiteMatcherTest.longAugmentingPathDoesNotOverflowStack` now runs on a 128 KB thread stack; a temporary recursive Kuhn under the same harness threw `StackOverflowError` (mutation check, then removed) |
| Lab max-flow capacities near `Long.MAX_VALUE` overflowed into a `500` | `AlgorithmLabIT.edgeCapacityAboveTheLimitGives400` (was 500, now `400 VALIDATION_FAILED`) |
| Documentation drift: a nonexistent `AnalyticsService`, `analytics` package, CORS config and map page; the old lock constant; `k = n/2`; the IEEE rank example; "roughly even" selection results | Corrected against the code and the exported data (no test applies) |

| Command | Result |
|---|---|
| `backend: ./mvnw -B verify` | BUILD SUCCESS. Surefire 186/186, Failsafe 85/85 |
| `frontend: npx tsc -b --noEmit` / `npm run lint` / `npx vitest run` / `vite build` | exit 0 / exit 0 / 15 files, 57/57 passed / built |
