# API Documentation

This reference describes the implemented API only. The live OpenAPI document is served at `/v3/api-docs`, and
Swagger UI at `/swagger-ui.html`. Both include the bearer-token scheme, so you can authorise once and try every
endpoint.

- **Base path:** `/api/v1`. Every path below is relative to it.
- **Content type:** `application/json` for request and response bodies.
- **Units (D1):** power in kW and energy in kWh, each with at most two decimals. Internally they are integer W and
  Wh.
- **Time (D2):** ISO-8601 timestamps with an explicit offset or `Z`, for example `2026-11-05T10:00:00Z` or
  `2026-11-05T15:30+05:30`. Seconds are optional, and every value is normalised to UTC. A timestamp without a zone
  is rejected with `400`. Booking windows are half-open, `[start, end)`, and lie on 15-minute boundaries.
- **Paging:** paged lists accept `page` (from 0) and `size` (1–100, default 20), and return
  `{content, page, size, totalElements, totalPages}`.

## Authentication and roles

`POST /auth/login` returns a JWT (HS256, valid for 8 hours). Send it as `Authorization: Bearer <token>`.

| Role | Can call |
|---|---|
| Public | `GET /health`, `POST /auth/register`, `POST /auth/login`, the OpenAPI and Swagger paths |
| `DRIVER` | `/auth/me`, `/stations/**`, `/vehicles/**`, `/bookings/**`, `/sessions/**`. Data is scoped to the caller: another driver's vehicle, booking or session returns `404`, never `403` |
| `OPERATOR` | Everything a driver can call, plus `/operator/**`, `/algorithms/**`, `/simulations/**`, `/benchmarks/**` |

Self-registration always creates a `DRIVER`. Operators come from the optional bootstrap account
(`CHARGEWISE_BOOTSTRAP_OPERATOR_*`) or from the demo seed.

## Error contract (D8)

Every error response, including Spring's own 404/405/415 and the security 401/403, has this shape:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "fieldErrors": [{ "field": "requestedStart", "message": "must be on a 15-minute boundary (:00, :15, :30 or :45)" }],
  "timestamp": "2026-11-02T11:05:36.200Z",
  "path": "/api/v1/bookings"
}
```

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation, malformed JSON (the field path is named), a timestamp without a zone, an unknown enum value, a duplicate or unknown id in an Algorithm Lab input |
| 401 | `UNAUTHORIZED` | Missing, invalid or expired token; wrong email or password (the same message in both cases) |
| 403 | `FORBIDDEN` | A driver calling an operator endpoint |
| 404 | `NOT_FOUND` | A missing resource, or one owned by another driver |
| 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | Wrong HTTP method or content type |
| 409 | `CONFLICT` | Duplicate email or registration, an overlapping active booking for the same vehicle, or a full benchmark queue |
| 409 | `INVALID_STATE` | A state transition that is not allowed, such as cancelling a completed booking or comparing a simulation before its first run |
| 413 | `INPUT_TOO_LARGE` | An Algorithm Lab input above its caps |
| 500 | `INTERNAL_ERROR` | Anything unexpected. No stack trace or exception message is exposed |

## Health

`GET /health` returns `200`:

```json
{"status":"UP","database":"UP","version":"0.0.1-SNAPSHOT"}
```

## Auth

| Method and path | Body | Response |
|---|---|---|
| `POST /auth/register` | `{name (≤120), email, password (8–72)}` | `201` `UserResponse` |
| `POST /auth/login` | `{email, password}` | `200` `{accessToken, tokenType:"Bearer", expiresAt, user}` |
| `GET /auth/me` | — | `200` `UserResponse {id, name, email, role, createdAt}` |

Emails are trimmed and compared case-insensitively. Passwords are stored as `{bcrypt}` hashes with cost 12.

## Stations

| Method and path | Parameters | Response |
|---|---|---|
| `GET /stations` | `status?`, `page`, `size` (1–100) | Page of `StationResponse {id, name, address, city, state, latitude, longitude, gridCapacityKw, status, connectors[]}` |
| `GET /stations/all` | — | Every `StationResponse` in id order, for the network map and directory (about 1 MB of JSON for the demo network, 139 KB gzipped) |
| `GET /stations/nearby` | `lat ∈ [-90,90]`, `lng ∈ [-180,180]`, `radiusKm ∈ (0,100]` (default 10) | `[{station, distanceKm}]`, nearest first (haversine) |
| `GET /stations/{id}` | — | `StationResponse` |
| `GET /stations/{id}/availability` | `from`, `to`: slot-aligned, at most 96 slots | `{stationId, from, to, slotMinutes:15, operationalConnectors, slots:[{start, end, freeConnectors, freeByType}]}` |

A connector counts as free in a slot when it is `OPERATIONAL`, its station is `OPERATIONAL`, and no `ASSIGNED`
booking holds it in that slot.

## Vehicles (driver-owned)

| Method and path | Body | Response |
|---|---|---|
| `POST /vehicles` | `{registrationNumber, batteryCapacityKwh, currentSoc (0–100), maxChargingKw, connectorTypes[]}` | `201` `VehicleResponse` |
| `GET /vehicles` | — | The caller's vehicles |
| `GET /vehicles/{id}` | — | `VehicleResponse` |
| `PUT /vehicles/{id}` | Same as `POST` | `VehicleResponse` |

Registrations are normalised to upper case without spaces or hyphens (`mh-01 ab 1234` becomes `MH01AB1234`), and
a duplicate returns `409`. Connector types: `CCS2`, `TYPE2_AC`, `CHADEMO`, `BHARAT_DC001`, `BHARAT_AC001`.

## Bookings (charging requests)

`POST /bookings`

```json
{"vehicleId": 7, "stationId": 3, "requestedEnergyKwh": 20,
 "requestedStart": "2026-11-05T10:00:00Z", "requestedEnd": "2026-11-05T11:00:00Z"}
```

The booking is created `PENDING` and returns `201`. Validation runs in this order, and each failure names the
field to fix:

1. `requestedStart` and `requestedEnd` are on 15-minute boundaries.
2. `requestedEnd` is after `requestedStart`, and the window is at most 48 slots (12 h).
3. `requestedStart` is in the future and at most 14 days ahead.
4. The vehicle is the caller's (otherwise `404`), and the station exists (otherwise `404`).
5. `requestedEnergyKwh ≤ battery × (100 − SoC) / 100`.
6. `requestedEnergyKwh ≤ maxChargingKw × window hours`.
7. The vehicle has no overlapping `PENDING`, `WAITLISTED` or `ASSIGNED` booking (`409`). The database exclusion
   constraint backs this check.

| Method and path | Response |
|---|---|
| `GET /bookings` | The caller's bookings, newest first, paged |
| `GET /bookings/{id}` | `BookingResponse` |
| `PATCH /bookings/{id}/cancel` | `BookingResponse`. Allowed only from `PENDING`, `WAITLISTED` or `ASSIGNED`, and only before the window starts (otherwise `409 INVALID_STATE`). An assigned booking's scheduled session is cancelled too |

`BookingResponse` fields: `id`, `vehicleId`, `vehicleRegistration`, `stationId`, `stationName`, `connectorId`,
`connectorType`, `requestedEnergyKwh`, `requestedStart`, `requestedEnd`, `status`, `statusReason`, `sessionId`,
`allocatedPowerKw`, `createdAt`, `assignedAt`.

**Status reasons:**

- `REJECTED` (the request can never be served as submitted): `NO_COMPATIBLE_CONNECTOR_TYPE`,
  `INSUFFICIENT_CONNECTOR_POWER`, `WINDOW_EXPIRED`, `ENERGY_EXCEEDS_BATTERY_HEADROOM`.
- `WAITLISTED` (eligible again in the next run): `STATION_NOT_OPERATIONAL`, `CONNECTORS_OUT_OF_SERVICE`,
  `CONNECTORS_BOOKED_IN_WINDOW`, `LOST_CONTENTION`, `INSUFFICIENT_GRID_CAPACITY`, `STATION_OUTAGE`,
  `CONNECTOR_OUTAGE`.

## Sessions (driver-owned, simulated)

| Method and path | Response |
|---|---|
| `GET /sessions/history` | The caller's sessions, newest first, paged |
| `GET /sessions/{id}` | `SessionResponse {id, bookingId, stationId, stationName, connectorId, connectorType, vehicleRegistration, status, windowStart, windowEnd, startTime, endTime, requestedEnergyKwh, energyDeliveredKwh, allocatedPowerKw}` |

Every 60 s the lifecycle job moves `SCHEDULED` sessions to `ACTIVE` once their window starts, and to `COMPLETED`
once it ends. Delivered energy is `min(allocatedKw × elapsed h, requested)`, rounded down to Wh. `startTime` and
`endTime` are the window bounds. Set `CHARGEWISE_LIFECYCLE_ENABLED=false` to turn the job off.

## Operator: stations and outages

| Method and path | Body | Effect |
|---|---|---|
| `POST /operator/stations` | `{name, address, city?, state?, latitude, longitude, gridCapacityKw, status}` | `201` `StationResponse`; a blank city or state is stored as null |
| `PUT /operator/stations/{id}` | Same | Applies the D5 policy below in the same transaction |
| `POST /operator/stations/{id}/connectors` | `{connectorType, maxPowerKw, status}` | `201` `ConnectorResponse` |
| `PATCH /operator/connectors/{id}/status` | `{status: OPERATIONAL\|OUT_OF_SERVICE}` | Applies the D5 policy below |
| `POST /operator/demo/reset` | — | Reloads the demo dataset and returns `{users, stations, connectors, vehicles, bookings}` |

**D5 policy:**

- **Station `OFFLINE`/`MAINTENANCE` or connector `OUT_OF_SERVICE`:**
  - `ASSIGNED` bookings whose window has not started become `WAITLISTED` (`STATION_OUTAGE` or
    `CONNECTOR_OUTAGE`), lose their connector, and have their session `CANCELLED`.
  - A window already under way has its session `INTERRUPTED`, and its booking ends `COMPLETED` with the outage
    reason.
- **Grid capacity reduced:**
  - Every live session at the station is re-allocated with Edmonds-Karp, using the new limit as source capacity.
  - Each session gets a new `power_allocations` row.
  - A not-yet-started session left with 0 kW is waitlisted with `INSUFFICIENT_GRID_CAPACITY`.

## Operator: allocation

`POST /operator/allocation/run` accepts the body `{intervalStart?, intervalEnd?}`. Both fields are optional; the
interval defaults to the current slot through 15 days later, which is also the maximum span. The call returns
`201` with `{id, intervalStart, intervalEnd, createdAt, result}`.

The pipeline (D4) runs in one transaction, serialised by a PostgreSQL advisory lock:

1. Lock the eligible bookings, plus any queued booking whose window has already started.
2. Expire started windows and re-check battery headroom.
3. Compute Quickselect percentiles of requested power.
4. Build the compatibility graph.
5. Run maximum bipartite matching.
6. Compute segment-tree headroom per station.
7. Run Edmonds-Karp.
8. Run the independent capacity verifier.
9. Persist the results.

Any failure rolls back everything, including the run record. Station updates, connector status changes and booking
cancellations take the same lock, so they wait for a running allocation to commit.

**`result` fields:**

- **Algorithm names:** `matchingAlgorithm`, `powerAlgorithm`, `selectionAlgorithm`.
- **Counts:** `eligibleRequests`, `assignedCount`, `waitlistedCount`, `rejectedCount`.
- **`assignments`:** `[{bookingId, stationId, connectorId, sessionId, requestedKw, allocatedKw}]`.
- **`unassigned`:** `[{bookingId, stationId, status, reason}]`.
- **`stations`:** `[{stationId, gridKw, demandKw, demandCapacityRatio, committedPeakKw, availableKw, allocatedKw}]`.
- **`bottlenecks`:** `[{kind, stationId | bookingId, capacityKw}]`.
- **`requestedPower`:** `{p50Kw, p75Kw, p90Kw, seed, comparisons, swaps}`, or `null` when no request was eligible.
- **`metrics`:** edges, cardinality, operation counts, kW totals and runtimes in ns.

`GET /operator/allocation/{id}` returns the stored run.

**Objective (verbatim).** Matching maximises the *number* of request–connector assignments in the modeled
compatibility graph. Edmonds-Karp maximises the *total feasible power* in the modeled network. Neither minimises
waiting time or guarantees fairness.

## Operator: monitoring

| Method and path | Response |
|---|---|
| `GET /operator/dashboard` | `{stations, connectors, bookings, sessions}` (counts for every status, zeros included); `utilisation {activeSessions, operationalConnectors, connectorUtilisation, activePowerKw, operationalGridKw, powerUtilisation}`; `waiting {algorithm, assignedSampleSize, p50/p90AssignWaitSeconds, queueSize, p50/p90QueueAgeSeconds}`; `latestAllocationRunId`; `generatedAt` |
| `GET /operator/requests?status&page&size` | Every driver's bookings, newest first |
| `GET /operator/sessions?status&page&size` | Every session, newest first |

Waiting time follows D11: `assigned_at − created_at`, plus the age of queued requests. Percentiles are nearest-rank
values selected by randomized Quickselect, and are `null` when the sample is empty.

## Algorithm Lab (operator)

Each endpoint runs the core algorithm and its baseline on the same input and reports results, operation counts
and `runtimeNs`.

**Caps:** 5,000 vertices, 50,000 edges and 1,000,000 values. Inputs beyond them return `413`. A duplicate or
unknown id returns `400`.

| Path | Input | Output |
|---|---|---|
| `POST /algorithms/matching/execute` | `{requests[], connectors[], edges:[{request, connector}]}` | `algorithm` (`BIPARTITE_MATCHING`) and `baseline` (`GREEDY_FIRST_AVAILABLE`), each `{cardinality, pairs, unmatched, edgesExamined, augmentations, reassignments, valid, runtimeNs}` |
| `POST /algorithms/max-flow/execute` | Exactly one of `network {nodes[], edges:[{from,to,capacity}], source, sink}` or `power {stations:[{id,capacityKw}], sessions:[{id,station,connectorMaxKw,requestedKw,vehicleMaxKw}], feeders?:[{id,capacityKw}], links?:[{feeder,station}]}` | `mode: NETWORK` returns Edmonds-Karp `{totalFlow, minCutCapacity, edges, minCut, augmentations, bfsRuns, edgesScanned}`. `mode: POWER` returns `EDMONDS_KARP` vs `GREEDY_PRIORITY`, each `{totals, stations, sessions, bottlenecks, operations, violations}` |
| `POST /algorithms/quickselect/execute` | `{values[], k? (1-based) \| percentiles? (percent, (0,100]), seed? (default 42)}` | `{n, seed, agree, selections:[{label, rank (0-based index in sorted order), value, baselineValue}], algorithm, baseline}` (`RANDOMIZED_QUICKSELECT` vs `FULL_SORT`) |
| `POST /algorithms/segment-tree/query` | `{values[], operations:[{type: SUM\|MAX, from, to} \| {type: SET\|ADD, index, value}]}` | `{agree, results:[{operation, type, from, to, tree, naive}], algorithm, baseline}` (`SEGMENT_TREE` vs `NAIVE_SCAN`) |

**Segment-tree limits:** at most 1,000 operations; `queries × values ≤ 50,000,000`, so the naive baseline stays
bounded; and `|value| ≤ 10^12`.

**Max-flow limits:** network capacities are integers in `0 … 10^12`, so no flow or cut sum over the 50,000-edge cap
can overflow; a larger capacity returns `400 VALIDATION_FAILED`. Power-mode kW fields take at most two decimals, with
up to 8 integer digits for stations and feeders and 6 for sessions.

**Examples:**

- The matching input `R1–C1, R1–C2, R2–C1` gives cardinality 2 for matching and 1 for greedy.
- The CLRS Figure 26.1 network gives a flow of 23.
- With feeders `F0 → {A, B}` and `F1 → {A}` (100 kW each), Edmonds-Karp delivers 200 kW and greedy delivers 100 kW.

## Simulations (operator)

A simulation stores a seeded scenario (D9) and runs it twice on the identical generated input: once with the
algorithms and once with the greedy baselines (D10). The input is identified by the SHA-256 of its canonical text.

| Method and path | Body | Response |
|---|---|---|
| `POST /simulations` | `{name (≤160), scenario, seed, stations? (1–100), connectorsPerStation? (1–16), requests? (0–5,000), horizonSlots? (1–192), maxWaitSlots? (0–32)}` | `201` `SimulationResponse {id, name, seed, configuration, status: PENDING, createdAt}`. Shape fields left out take the scenario's preset |
| `GET /simulations` | — | The 50 most recent simulations, newest first |
| `POST /simulations/{id}/run` | — | `{simulation, inputHash, reproducedPreviousRun?}`. A re-run replaces the stored results; `reproducedPreviousRun` is `true` when the input hash and every non-timing metric of both strategies match the previous run |
| `GET /simulations/{id}/results` | — | One result per strategy (`ALGORITHM`, `BASELINE`): `{id, strategy, algorithmName, algorithmVersion, inputHash, metrics, runtimeNs, createdAt}`; empty before the first run |
| `GET /simulations/{id}/comparison` | — | `{simulationId, name, scenario, seed, inputHash, algorithm, baseline, rows:[{metric, label, unit, algorithm, baseline, difference, betterWhen: HIGHER\|LOWER}]}`. `409 INVALID_STATE` before the first run |

**Scenarios:** `NORMAL`, `PEAK_DEMAND`, `CONNECTOR_OUTAGE`, `STATION_OUTAGE`, `GRID_REDUCTION`,
`MIXED_COMPATIBILITY`, `HIGH_CONTENTION`, `SHARED_FEEDER`.

**Metrics** include requests started, expired and pending at the horizon end; sessions completed and interrupted;
energy requested and delivered (kWh); power requested, allocated and unserved (kW summed over 15-minute slots);
connector utilisation overall and per station; wait-to-start p50 and p90 (minutes, Randomized Quickselect,
nearest rank); capacity and matching violations; edges examined, power operations and runtimes.

**Per-slot evidence:** an `ALGORITHM` run also runs the baseline, untimed and not applied, on each slot's identical
graph and power problem. `slotsWhereBaselineMatchedMore` and `slotsWhereBaselineAllocatedMore` count the slots
where greedy did better (always 0), and `matchedGainOverBaseline` and `allocatedGainOverBaselineKw` sum the
algorithm's gain over those slots. Horizon totals of the two runs come from different histories and are reported
as measured, never as a guarantee.

## Benchmarks (operator)

A benchmark run times each algorithm against its baseline on identical inputs generated from the seed. Both answers
are verified (matching validity, capacity limits, equal selected values, equal range answers) before either side is
timed.

| Method and path | Body | Response |
|---|---|---|
| `POST /benchmarks/run` | `{seed, sizes: [SMALL\|MEDIUM\|LARGE] (≥1), families?: [MATCHING\|POWER\|POWER_FEEDERS\|SELECTION\|RANGE_QUERY] (default all), trials? (1–31, default 7), warmups? (0–10, default 3)}` | `202` `{id, seed, configuration, status: PENDING, createdAt}`. Runs queue on one benchmark thread so they never overlap; `409 CONFLICT` when 4 runs are already waiting |
| `GET /benchmarks` | — | The 20 most recent runs, newest first, without reports |
| `GET /benchmarks/{id}` | — | The run; once `status` is `COMPLETED` it carries `report`. Poll until `COMPLETED` or `FAILED` |

**Sizes:** `SMALL` 100 requests, 30 connectors and 1,000 values; `MEDIUM` 1,000, 300 and 10,000; `LARGE` 10,000,
3,000 and 100,000. Matching runs at densities 2, 8 and 32 compatible connectors per request; power allocation at 10
and 100 stations with one session per connector, with and without shared feeders; selection takes the median of 5
distributions (uniform, sorted, reverse sorted, duplicate-heavy, all equal); range queries replay 1,000 operations
(900 queries, 100 point updates).

**Report:** `{seed, warmups, trials, sizes, families, environment {javaVersion, javaVendor, vmName,
availableProcessors, maxHeapBytes, osName, osArch}, cases[], totalNs}`. Each case is `{family, label, size,
parameters, correct, algorithm, baseline, algorithmQuality, baselineQuality, qualityUnit, outcomes}`, where
`algorithm` and `baseline` are `{name, trials, medianNs, q1Ns, q3Ns, iqrNs, minNs, maxNs, operationsMedian,
operationsMin, operationsMax, allocatedBytes}` (nearest-rank quartiles; operations `-1` where the implementation
has no counter; `allocatedBytes` from one extra untimed run, `null` where the JVM cannot count it). A case that fails
verification has `correct: false` and no measurements. Quality is matched requests, watts allocated, the selected
value, or a checksum of the range answers (modulo 1,000,000,007).
