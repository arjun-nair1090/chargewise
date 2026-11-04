# ChargeWise — Architecture and Design Decisions

Status: established in Phase 0 (2026-11-02). Source of truth for requirements is
`PROJECT_SPEC.md`; this document records *how* the spec is realised and every
modeling decision the spec leaves open. Changes to a decision must be recorded
here with a reason.

---

## 1. System overview

```text
┌──────────────────────────── Browser ────────────────────────────┐
│ React + TypeScript (Vite)                                       │
│  /driver/*  garage · stations · booking · sessions · history    │
│  /operator/* overview · allocation · requests · sessions        │
│              stations · analytics · algorithm lab               │
│              simulations · benchmarks                           │
└───────────────┬─────────────────────────────────────────────────┘
                │ REST/JSON  /api/v1  (Bearer JWT)
┌───────────────▼──────────── Spring Boot (Java 21) ──────────────┐
│ controller → service → repository → PostgreSQL (Flyway)         │
│                │                                                │
│                ├─ allocation pipeline (transactional)           │
│                ├─ simulation (deterministic, seeded)            │
│                ├─ dashboard + lab (Quickselect, Segment Tree)   │
│                └─ benchmark (harness + baselines)               │
│                         │                                       │
│        algorithm/  ◄────┘  framework-agnostic, no Spring imports│
│          matching · flow · power · selection · tree             │
└─────────────────────────────────────────────────────────────────┘
```

**Hard rule:** every assessed algorithm executes in Java on the server. The
frontend only renders backend responses; it never computes assignments,
allocations, percentiles or range aggregates.

## 2. Backend package layout

`backend/src/main/java/com/chargewise/`

| Package | Responsibility | May depend on |
|---|---|---|
| `algorithm.matching` | `BipartiteGraph`, `BipartiteMatcher` (DFS augmenting paths), `GreedyFirstAvailableMatcher`, `MatchingVerifier` | JDK only |
| `algorithm.flow` | `FlowNetwork` (residual graph), `EdmondsKarp` | JDK only |
| `algorithm.power` | `PowerAllocationProblem`, `MaxFlowPowerAllocator`, `GreedyPowerAllocator`, `CapacityVerifier` | `algorithm.flow` |
| `algorithm.selection` | `RandomizedQuickselect`, `SortingSelector`, `Percentiles` | JDK only |
| `algorithm.tree` | `ChargingAvailabilitySegmentTree`, `NaiveRangeScanner` | JDK only |
| `controller` | HTTP mapping, auth boundary, no business rules | `dto`, `service` |
| `dto` | Validated request/response records (never entities) | — |
| `service` | Workflows, transactions, domain→algorithm translation; dashboard percentiles (`DashboardService`) and the Algorithm Lab (`AlgorithmLabService`) | everything below |
| `entity`, `repository` | JPA model and Spring Data repositories | — |
| `security` | JWT issuing/validation, password hashing, role checks | — |
| `simulation` | Seeded scenario generator + slot-by-slot executor | `algorithm.*` |
| `benchmark` | Timed harness (`BenchmarkRunner`) and seeded inputs (`BenchmarkInputs`); persistence lives in `service` | `algorithm.*` |
| `exception` | `ApiException` hierarchy + `@RestControllerAdvice` | — |
| `config` | Clock, OpenAPI, security filter chain, Jackson instant deserializer, scheduling, benchmark executor | — |

The `algorithm.*` packages contain no Spring, JPA or Jackson imports. They
operate on primitive arrays and indices; translation to and from domain
identifiers happens in `service` (for example `CompatibilityGraphBuilder` and
`PowerProblemBuilder`). An ArchUnit-style check (a plain JUnit test that scans
imports) enforces this, and holds `simulation` and `benchmark` to the same rule.

## 3. Units and numeric representation (D1)

- Persisted power is `NUMERIC(8,2)` kW and energy is `NUMERIC(8,2)` kWh, mapped
  to `BigDecimal`.
- Inside algorithms, power is a `long` in **watts** and energy is a `long` in
  **watt-hours**. Conversion is `kw.movePointRight(3).longValueExact()`. API
  validation (`@Digits(fraction = 2)`) guarantees at most two decimals, so the
  conversion is exact and never rounds.
- Converting back to kW uses `BigDecimal.valueOf(watts, 3)` and then
  `setScale(2, RoundingMode.DOWN)`. Rounding down can never report more power
  than was allocated.
- Capacity checks compare `long` values only. Business constraints never use
  floating-point equality.
- Sums use `Math.addExact`, so an overflow throws instead of wrapping silently.

## 4. Time model (D2)

- All timestamps are UTC `Instant` / `TIMESTAMPTZ`. The API accepts only ISO-8601
  with an explicit offset or `Z`; a local timestamp with no zone is rejected
  with `400 VALIDATION_FAILED`.
- **Slot length is 15 minutes.** Booking `requestedStart` and `requestedEnd`
  must sit on slot boundaries. The window is half-open `[start, end)`, at least
  1 slot and at most 48 slots (12 h) long, and starts no more than 14 days ahead.
- A booking reserves its connector for the **whole requested window**. Two
  windows overlap iff `a.start < b.end && b.start < a.end`.
- **Requested power** for a booking is the average power needed to deliver the
  requested energy within the window, capped by the vehicle:
  `requestedW = min(ceil(energyWh * 60 / windowMinutes), vehicleMaxW)`.
- The constant-power model applies: a session's allocated power is constant
  over its window. Tapering and losses are not modeled.

## 5. Statuses and lifecycle (D3)

| Entity | Stored statuses |
|---|---|
| Station | `OPERATIONAL`, `MAINTENANCE`, `OFFLINE` |
| Connector | `OPERATIONAL`, `OUT_OF_SERVICE` (occupancy is *derived* from bookings, never stored) |
| Booking | `PENDING` → `ASSIGNED` \| `WAITLISTED` \| `REJECTED`; `CANCELLED`; `COMPLETED` |
| ChargingSession | `SCHEDULED` → `ACTIVE` → `COMPLETED`; `CANCELLED`; `INTERRUPTED` |

- `REJECTED` covers *structural* impossibility: the vehicle has no connector type
  that the station offers, the energy exceeds the battery headroom, or the
  window has expired.
- `WAITLISTED` covers *temporary* impossibility: compatible connectors are
  out of service or booked in the window, or the request lost contention.
  Waitlisted requests stay eligible for later allocation runs until their window
  starts.
- `SessionLifecycleService.advance(Instant now)` moves sessions to `ACTIVE` or
  `COMPLETED` and computes simulated `energy_delivered_kwh = allocatedKw × elapsed h`,
  capped at the requested energy. It runs from a `@Scheduled` job every 60 s and
  is called directly with a fixed `Clock` in tests.

## 6. Allocation pipeline (D4)

Input: an allocation interval `[intervalStart, intervalEnd)` aligned to slots.
The whole pipeline runs in a single `@Transactional` service method:

1. `AllocationLock.acquire()` takes `pg_advisory_xact_lock(AllocationLock.ID)`,
   which serialises concurrent runs. Station updates, connector status changes
   and booking cancellations take the same lock first, so none of them can
   change the rows a run is planning with until that run commits.
2. Load `PENDING` and `WAITLISTED` bookings whose window overlaps the interval
   (`SELECT … FOR UPDATE`), plus any queued booking whose window has already
   started, whatever interval it falls in, so a request no earlier run reached
   is still closed out in step 3, plus stations, connectors and existing `ASSIGNED`
   bookings that overlap any candidate window.
3. Validate: expire windows that have already started (→ `REJECTED`,
   `WINDOW_EXPIRED`) and re-check the battery headroom.
4. **Quickselect analytics:** compute p50, p75 and p90 of requested power over
   the eligible requests, and per-station demand/capacity ratios. These are
   stored in the run's metrics.
5. **Compatibility graph.** `U` = eligible requests, `V` = connectors. The edge
   `(u, v)` exists iff *all* of the following hold:
   - `v` is at the station the booking names (`bookings.station_id`);
   - `v.connectorType ∈ vehicle.connectorTypes`;
   - the station is `OPERATIONAL` and the connector is `OPERATIONAL`;
   - no `ASSIGNED` booking on `v` overlaps `u`'s window;
   - `min(v.maxPowerW, vehicle.maxW) × windowHours ≥ requestedEnergyWh`, so the
     connector can physically deliver the energy in time.

   When `u` ends up with no edges, the builder records the *first* failing rule
   as its reason: `NO_COMPATIBLE_CONNECTOR_TYPE`, `STATION_NOT_OPERATIONAL`,
   `CONNECTORS_OUT_OF_SERVICE`, `CONNECTORS_BOOKED_IN_WINDOW`,
   `INSUFFICIENT_CONNECTOR_POWER`. An unmatched `u` that had edges gets
   `LOST_CONTENTION`.
6. **Bipartite matching** (`BipartiteMatcher`). Every connector receives at most
   one new booking per run. Combined with the existing-overlap check on each
   edge, this means the run can never create overlapping bookings on a
   connector. This is conservative: two new non-overlapping requests on the same
   connector are served across two runs. The behaviour is documented as a
   limitation.
7. **Station capacity available to the run.** For each station, a
   `ChargingAvailabilitySegmentTree` is built over the 15-minute slots of the
   batch horizon `[min start, max end)`. Each leaf holds the committed watts of
   existing sessions in that slot. The available capacity is then
   `availableW = gridW − rangeMax(horizon)`, floored at 0.

   *Safety argument:* every new session at the station lies inside the horizon,
   and the new allocations sum to at most `availableW`. So for every slot
   `t`, `committed(t) + new(t) ≤ max committed + Σ new ≤ gridW`.
8. **Edmonds-Karp power allocation** (`MaxFlowPowerAllocator`) over the matched
   sessions. The network has these edges:
   - `source → station` with capacity `availableW`;
   - `station → session` with capacity `connector.maxPowerW`;
   - `session → sink` with capacity `requestedW` (already capped by the
     vehicle's maximum power).
9. `CapacityVerifier` independently re-checks every station, connector and
   vehicle limit. A violation aborts the transaction (`IllegalStateException`)
   and nothing is persisted.
10. Persist bookings (`ASSIGNED` with `connector_id`, `assigned_at` and
    `allocation_run_id`), sessions (`SCHEDULED`), `power_allocations`, and one
    `allocation_runs` row containing the full result JSON: algorithm names and
    versions, assignments, unmatched requests with reasons, station and session
    allocations, bottlenecks, percentiles, runtimes and operation counts.
11. Any exception rolls back the whole transaction, so partial bookings cannot
    exist. As a last line of defence, the database exclusion constraint (D6)
    rejects overlapping connector reservations even if service logic is bypassed.

**Objective statement (used verbatim in UI and report).** Matching maximises the
*number* of request–connector assignments in the modeled compatibility graph.
Edmonds-Karp maximises the *total feasible power* in the modeled network.
Neither minimises waiting time or guarantees fairness.

## 7. Outage and grid-reduction policy (D5)

- **Connector → `OUT_OF_SERVICE`** or **station → `OFFLINE`/`MAINTENANCE`:**
  - `ASSIGNED` bookings whose window has not started become `WAITLISTED`
    (reason `CONNECTOR_OUTAGE` or `STATION_OUTAGE`), their connector is cleared,
    and their session is `CANCELLED`.
  - Sessions already in progress become `INTERRUPTED`.
  - The next allocation run may re-match the waitlisted bookings to another
    compatible connector at the same station.
- **Grid capacity reduction:** within the same transaction, all non-completed
  sessions at the station are re-allocated with Edmonds-Karp. Source capacity
  is the new grid limit, and all affected sessions are conservatively treated as
  concurrent. Because `Σ allocations ≤ new limit`, no slot can exceed the limit.
  New `power_allocations` rows record the change, so the history is preserved.

## 8. Database (D6)

The spec's suggested tables are used, with these documented additions:

- `bookings`: `assigned_at TIMESTAMPTZ NULL`, `status_reason VARCHAR(40) NULL`,
  `allocation_run_id BIGINT NULL REFERENCES allocation_runs(id)`.
- `allocation_runs` (`id`, `interval_start`, `interval_end`, `created_by`,
  `result JSONB`, `created_at`) backs `GET /operator/allocation/{id}`.
- `benchmark_runs` (`id`, `created_by`, `random_seed`, `configuration JSONB`,
  `results JSONB`, `status`, `created_at`) backs `GET /benchmarks/{id}`.
- `CREATE EXTENSION btree_gist` and these exclusion constraints:

```sql
ALTER TABLE bookings ADD CONSTRAINT bookings_no_connector_overlap
  EXCLUDE USING gist (connector_id WITH =,
                      tstzrange(requested_start, requested_end, '[)') WITH &&)
  WHERE (status = 'ASSIGNED' AND connector_id IS NOT NULL);

ALTER TABLE bookings ADD CONSTRAINT bookings_no_vehicle_overlap
  EXCLUDE USING gist (vehicle_id WITH =,
                      tstzrange(requested_start, requested_end, '[)') WITH &&)
  WHERE (status IN ('PENDING', 'WAITLISTED', 'ASSIGNED'));
```

- `CHECK` constraints mirror the Bean Validation rules (`soc BETWEEN 0 AND 100`,
  capacities `> 0`, `grid_capacity_kw >= 0`, `requested_end > requested_start`).
- Indexes cover station status, connector (station, status), booking
  (status, requested_start), session (station, status) and simulation result
  `simulation_id`.

## 9. Security (D7)

- Authentication is stateless JWT, signed HS256 and issued by
  `POST /auth/login`. Spring Security's resource-server support validates it, so
  no third-party JWT library is needed. The signing secret is read from
  `CHARGEWISE_JWT_SECRET`. The token lifetime is 8 h.
- Passwords are hashed with `BCryptPasswordEncoder` (strength 12) behind
  Spring's `DelegatingPasswordEncoder`.
- Roles are `DRIVER` and `OPERATOR`. `/api/v1/operator/**`, `/simulations/**`,
  `/benchmarks/**` and `/algorithms/**` require `OPERATOR`. Driver resources
  are always filtered by the authenticated user ID in the *repository query*,
  not after loading. A foreign ID returns `404`, which avoids confirming that the
  resource exists.
- Public self-registration always creates a `DRIVER`. Operator accounts come
  from the seed data or the `CHARGEWISE_BOOTSTRAP_OPERATOR_*` environment
  variables.
- Algorithm-lab endpoints cap input size (≤ 5,000 vertices / 50,000 edges /
  1,000,000 array elements) and return `413 INPUT_TOO_LARGE` beyond that.
  Max-flow edge capacities are capped at 10^12 (`400` beyond), so no flow sum
  can overflow a `long`.

## 10. Error contract (D8)

Every error response has this shape:

```json
{ "status": 400, "code": "VALIDATION_FAILED", "message": "Request validation failed",
  "fieldErrors": [{ "field": "requestedEnd", "message": "must be after requestedStart" }],
  "timestamp": "2026-11-05T10:00:00Z", "path": "/api/v1/bookings" }
```

The codes are `VALIDATION_FAILED`, `NOT_FOUND`, `FORBIDDEN`, `UNAUTHORIZED`,
`CONFLICT` (duplicates or overlaps), `INPUT_TOO_LARGE`, `INVALID_STATE` and
`INTERNAL_ERROR`. Stack traces never reach clients.

## 11. Randomness and reproducibility (D9)

- All randomness comes from `java.util.SplittableRandom(seed)`. Its algorithm is
  specified in the JDK, so results are reproducible across JVMs.
- Quickselect receives a `java.util.random.RandomGenerator` by injection.
- Each simulation stores its configuration and seed. The generator draws values
  in a fixed, documented order. A run stores a SHA-256 of the canonical
  generated input, so a re-run can *prove* that its input is identical.
  Re-runs must reproduce every non-timing output exactly; runtimes are excluded
  from the equality check.

## 12. Simulation model (D10)

The simulation runs in memory and is independent of the live booking tables.
Results are persisted in `simulations` and `simulation_results`.

- Discrete 15-minute slots over a horizon of `H` slots.
- Each generated request has an arrival slot, a latest-start slot
  (`arrival + maxWaitSlots`), a vehicle, connector types, an energy need and a
  station.
- Each slot runs these steps:
  1. Eligible requests are those that have arrived, are unserved and have not
     expired.
  2. Free connectors are operational connectors that no session currently
     occupies.
  3. Matching assigns requests to free connectors.
  4. Edmonds-Karp allocates power to *all* active sessions in the slot:
     `source → station` = grid capacity × scenario factor,
     `station → session` = connector max power,
     `session → sink` = `min(vehicleMax, remainingWh × 4)` (one slot is
     ¼ h).
  5. Delivered energy is added, and sessions that have reached their requested
     energy free their connector.
- **Waiting time** = start slot − arrival slot, in minutes. Expired requests are
  reported separately and never counted as waiting time zero.
- Scenarios: `NORMAL`, `PEAK_DEMAND`, `CONNECTOR_OUTAGE`, `STATION_OUTAGE`,
  `GRID_REDUCTION`, `MIXED_COMPATIBILITY`, `HIGH_CONTENTION`, and the D12
  `SHARED_FEEDER` scenario (§16).
- Every scenario runs twice on the identical generated input: once with the
  algorithms (matching + Edmonds-Karp) and once with the baselines (greedy +
  greedy). Both runs report the same metrics: request, matched and unmatched counts; requested, allocated
  and unserved kW; station utilisation; waiting-time percentiles; capacity violations; runtimes.
- **Per-slot evidence.** The two runs diverge after the first slot: different
  assignments leave different connectors free and different energy delivered,
  so horizon totals compare two histories. Matching started 85 sessions to
  greedy's 86 on `HIGH_CONTENTION` seed 3. Totals are therefore reported as
  *measured outcomes*, never as a guarantee. The guarantee is per slot: in the
  algorithm run, each slot also runs the baseline, untimed and not applied, on
  that slot's identical graph and power problem. `slotsWhereBaselineMatchedMore`
  and `slotsWhereBaselineAllocatedMore` count the slots where greedy did better
  (asserted 0 for all 8 scenarios × 5 seeds), and `matchedGainOverBaseline` and
  `allocatedGainOverBaselineKw` sum the algorithm's per-slot gain.
- The Segment Tree holds per-slot occupied-connector counts per station.
  Range-sum and range-max queries feed utilization and peak-occupancy charts.

## 13. Live waiting-time definition (D11)

Waiting time in the live application is measured as
`assigned_at − created_at` for assigned bookings. The live queue additionally
reports the age of each pending request. Percentiles of these values are
computed with `RandomizedQuickselect`. Demo seed data is generated with past
`created_at` values; each record's `source` field says what is real.

## 14. Frontend architecture

- React, TypeScript, Vite, Tailwind CSS v4 (design tokens in `@theme`),
  React Router (separate `DriverLayout` and `OperatorLayout`), TanStack Query
  (server state), react-hook-form + zod (forms mirroring server validation),
  Recharts, Leaflet / react-leaflet with OpenStreetMap tiles.
- `src/services/api.ts` is the only module that calls `fetch`. It attaches the
  JWT and maps the error contract (D8) to typed `ApiError`s.
- Every data view handles loading, empty, error and success states. A
  `DataBadge` marks simulated sessions and generated inputs wherever they are shown.
- Design tokens: background `#090B0A`, surface `#171B19`, accent `#B8F34A`,
  muted text `#8B9690`, primary text near-white `#F2F5F3`. Lime is reserved
  for primary actions, focus rings and the single most important number per
  view.

## 15. Known limitations (to carry into the report)

- One new booking per connector per allocation run (§6, step 6).
- The station capacity reservation is conservative: it uses the peak committed
  load over the batch horizon (§6, step 7).
- Each request names a single station, so the matching graph decomposes into
  per-station components.
- On the spec's three-layer network, each session hangs off exactly one
  station, so the network is a tree. Max-flow therefore equals
  `Σ_station min(available, Σ_sessions min(connector, demand))`, and a
  work-conserving greedy allocator reaches the same *total*. Edmonds-Karp still
  contributes the min-cut bottleneck certificate and generality. This is a
  theoretical expectation that the benchmarks must *measure*, not assume. The
  feeder extension (§16) is the documented case where routing choice exists.
- No battery tapering, conversion losses, live hardware or OCPP.

## 16. Extension D12 — shared distribution feeders

Approved 2026-11-02. In many real networks, several charging stations hang off
the same medium-voltage feeder, and some sites are dual-fed from two feeders
(a ring main). The power network then has genuine routing choices, which the
spec's three-layer model lacks.

```text
source ──feederCap──► feeder f ──stationCap──► stationIn i ──stationCap──► station i ──connMax──► session j ──min(req, vehicleMax)──► sink
```

- **`stationIn → station` gate.** This single edge carries the station's
  grid-import limit, so a dual-fed station can never import more than its
  limit, however many feeders supply it.
- **Special case.** With exactly one feeder per station and each feeder's
  capacity ≥ the station's limit, the network reduces to the spec §6.2 model.
- **Greedy baseline (feeder mode).** Sessions are processed in a fixed index
  order. Each session draws from its station's feeders in link order, so a
  dual-fed station can exhaust a feeder that a single-fed station depends on.
  Edmonds-Karp re-routes such flow through reverse residual edges. Unit test
  `feederRoutingBeatsFixedOrderGreedy` (EK 200 kW vs greedy 100 kW) pins the
  constructed counterexample. Any gain on generated scenarios is reported
  only as *measured*.
- **Feeder sizing.** A third as many feeders as stations (at least two);
  station `s` hangs off feeder `s mod F`, and 40% of stations are dual-fed from
  the next feeder too. In the `SHARED_FEEDER` scenario each feeder carries 40%
  of the limits of the stations it primarily serves (a diversity factor; at
  60% no feeder bound in per-slot demand). In the benchmarks every session
  demands at once, so 40% saturates every feeder under both allocators and they
  tie; they use 60%, where feeders bind unevenly. Both choices were probed and
  are recorded with their measured effect; only `algorithm ≥ baseline` is
  guaranteed.
- **Scope.** The extension is used by the `SHARED_FEEDER` simulation scenario,
  the `POWER_FEEDERS` benchmarks and the Algorithm Lab. The live booking pipeline keeps the spec model (no
  feeder data in the persisted schema), which keeps the database aligned with
  spec §9.
- **Objective unchanged.** Max-flow still maximises total feasible power only.
  It makes no fairness or waiting-time claim.
