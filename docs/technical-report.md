# ChargeWise — Technical Report

**Course:** Advanced Algorithm, CIA 3, Semester V, B.Tech CS AI & ML, 2026–27
**Team:** Student 1 *(name, ID)*, Student 2 *(name, ID)*

This report gathers the project's evidence into one place. Each section summarises a detailed document:
[architecture.md](architecture.md) (design decisions D1–D12), [algorithms.md](algorithms.md) (proofs and pseudocode),
[complexity-analysis.md](complexity-analysis.md), [benchmark-report.md](benchmark-report.md),
[test-report.md](test-report.md), [api-documentation.md](api-documentation.md) and
[database-schema.md](database-schema.md).

---

## 1. Problem statement and constraints

A charging network has a fixed set of **connectors**, each of one physical standard and with a maximum output. Each
**station** can import only a limited amount of power from the grid. Drivers ask for a quantity of energy within a
time window; operators must decide who charges where, and at what power.

ChargeWise makes two linked decisions per allocation run:

1. **Assignment.** Which pending request gets which connector.
   - A request can use a connector only when every one of these holds:
     - the connector is at the station the driver chose
     - its type is one the vehicle accepts
     - the station and connector are operational
     - no assigned booking on it overlaps the window
     - `min(connector max, vehicle max) × window hours ≥ requested energy`
   - Each connector takes at most one new request per run.
2. **Power.** How many watts each assigned session draws. Every grant must fit three limits:
   - the connector's rating
   - the vehicle's demand (`min(ceil(energy / hours), vehicle max)`)
   - the station's remaining grid headroom over the batch horizon

**Objective, stated exactly.** Matching maximises the *number* of valid request–connector assignments. Edmonds-Karp
maximises the *total* deliverable power. Neither minimises waiting time or guarantees fairness; the application
never claims either.

**Hard constraint.** Zero capacity violations, enforced three ways:

- an independent verifier (`CapacityVerifier`) re-checks every allocation and aborts the transaction on any violation
- a PostgreSQL exclusion constraint makes overlapping connector reservations impossible, even if service logic is
  bypassed
- every scenario and benchmark checks the same invariant

**Modelling assumptions** ([architecture.md §3–§5](architecture.md)): 15-minute slots; constant power within an
allocation interval; no battery tapering or conversion losses; power held as integer watts, so arithmetic is exact.
The demo network has 1,971 real stations across India from Open Charge Map and OpenStreetMap, with 4,614
connectors, partly estimated; grid limits and the 600 requests from 100 drivers are made up.

## 2. System design

```text
 Browser: React 19 + TypeScript (Vite)
   driver portal ── garage · find a station · requests · sessions
   operator console ── overview · allocation · requests · sessions · stations
                       analytics · algorithm lab · simulations · benchmarks
                │  REST/JSON /api/v1, Bearer JWT (driver / operator roles)
 Spring Boot 4, Java 21
   controller ─► service ─► repository ─► PostgreSQL 18 (Flyway migrations)
                    │
                    ├─ AllocationService ─ one transaction, advisory lock
                    ├─ SimulationService ─ seeded scenarios, input hash
                    ├─ DashboardService  ─ Quickselect percentiles for the overview
                    ├─ AlgorithmLabService ─ each algorithm against its baseline; the
                    │                        analytics range explorer uses its segment tree
                    └─ BenchmarkService  ─ single-thread queue, BenchmarkRunner
                    │
   algorithm/ (pure Java; no Spring, Jakarta or Jackson imports — enforced by AlgorithmPurityTest)
      matching · flow · power · selection · tree   + baselines + verifiers
```

**Rule.** Every assessed algorithm runs on the server. The frontend only renders API responses; it never computes
an assignment, allocation, percentile or range aggregate.

### 2.1 Allocation pipeline (D4)

`POST /api/v1/operator/allocation/run` performs these steps in one `@Transactional` method:

1. Take a PostgreSQL advisory lock, so concurrent runs serialise. Station edits, connector status changes and
   cancellations take the same lock, so they cannot change a run's inputs while it plans.
2. Load the pending and waitlisted requests that overlap the interval, plus any whose window has already started,
   together with stations, connectors and existing assignments.
3. Expire windows that have already started.
4. **Quickselect:** compute p50, p75 and p90 of requested power for the run's metrics.
5. Build the compatibility graph. Every request with no edges gets the first rule that excluded every connector
   as its reason.
6. **Bipartite matching** assigns connectors. A request that had edges but stayed unmatched is `LOST_CONTENTION`.
7. **Segment tree** per station over the batch horizon: `available = grid − rangeMax(committed load)`.
8. **Edmonds-Karp** allocates `available` among the matched sessions.
9. `CapacityVerifier` re-checks everything. Any violation rolls back the whole run.
10. Persist bookings, sessions and power grants, plus an `allocation_runs` row that holds the full evidence: pairs,
    unmatched reasons, bottlenecks, percentiles, runtimes and operation counts.

**Outages and grid cuts (D5).** Taking a connector or station out of service waitlists its future assignments and
interrupts sessions in progress; lowering a station's grid limit re-runs Edmonds-Karp over its live sessions inside
the same transaction.

### 2.2 API surface

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` |
| Driver | `GET /stations`, `/stations/all`, `/stations/nearby`, `/stations/{id}/availability`; `POST/GET/PUT /vehicles`; `POST/GET /bookings`, `PATCH /bookings/{id}/cancel`; `GET /sessions/history` |
| Operator | `POST/PUT /operator/stations`, `POST /operator/stations/{id}/connectors`, `PATCH /operator/connectors/{id}/status`; `POST /operator/allocation/run`, `GET /operator/allocation/{id}`; `GET /operator/dashboard`, `/operator/requests`, `/operator/sessions`. The Analytics page composes these with station availability and the Lab's segment-tree endpoint; it has no endpoint of its own |
| Algorithm Lab | `POST /algorithms/matching/execute`, `/algorithms/max-flow/execute`, `/algorithms/quickselect/execute`, `/algorithms/segment-tree/query`. Each runs the algorithm and its baseline on the same input |
| Evidence | `POST /simulations`, `POST /simulations/{id}/run`, `GET /simulations/{id}/comparison`; `POST /benchmarks/run`, `GET /benchmarks/{id}` |

**Error contract (D8).** Every error returns `{status, code, message, fieldErrors[], timestamp, path}` with a stable
`code`: `VALIDATION_FAILED`, `NOT_FOUND`, `FORBIDDEN`, `UNAUTHORIZED`, `CONFLICT` (duplicates or overlaps),
`INPUT_TOO_LARGE`, `INVALID_STATE` or `INTERNAL_ERROR`.

Stack traces never reach clients. Empty inputs, out-of-range values, duplicate registrations and overlapping
bookings are rejected with one of these codes, never answered with a silently wrong result. A network with no
feasible path is not an error: it returns a total of 0, with the zero-capacity limits reported as bottlenecks.

## 3. Algorithms: pseudocode and correctness

Full pseudocode, invariants and failure-case tables are in [algorithms.md](algorithms.md). This section gives each
algorithm's core and the argument for its correctness.

### 3.1 Bipartite matching — Kuhn's DFS augmenting paths (Unit 5)

```text
for each request u:
    clear visited marks
    if tryAugment(u): cardinality += 1
tryAugment(u):
    for connector v in adj(u), not visited:
        mark v visited
        if v is free or tryAugment(owner(v)):
            assign u to v; return true
    return false
```

The search uses an explicit stack, so an augmenting path through 10,000 requests cannot overflow the thread stack. A
test runs that case on a 128 KB thread stack, which a recursive version of the same search overflows.

**Correctness.**

- *Validity.* Each flip exchanges matched and unmatched edges along an alternating path, so the result stays a
  matching. `MatchingVerifier` checks this independently.
- *Maximality.* By Berge's theorem, a matching is maximum if and only if no augmenting path exists. A request whose
  search fails can never gain a path later.
- *Evidence.* `randomGraphsMatchBruteForceMaximum` compares 200 seeded random graphs against exhaustive search.

**Baseline.** Greedy first-available never revisits a choice. Its result is maximal but not maximum, and at least
half of maximum. In the constructed counterexample, greedy serves 1 request where matching serves 2, by reassigning
request 0 along an augmenting path.

### 3.2 Edmonds-Karp maximum flow — power allocation (Unit 5)

```text
loop:
    BFS from source over arcs with residual > 0
    if sink unreachable: the visited set is a minimum cut; stop
    b = minimum residual on the path
    push b along the path, and subtract b on each reverse arc
```

The network is `source → station (available W) → session (connector max) → sink (demand)`. Extension D12 inserts
shared distribution feeders between the source and the stations; dual-fed stations give the flow real routing
choices.

**Correctness.**

- *Feasibility.* An augmentation never pushes more than the minimum residual on its path, so `0 ≤ flow ≤ capacity`
  and conservation hold at every step.
- *Optimality.* When BFS stops, every arc leaving the visited set is saturated and every arc entering it is empty,
  so the flow equals that cut's capacity. That is the max-flow min-cut theorem.
- *Bottleneck report.* The cut edges are mapped back to the limits that bind: `FEEDER`, `STATION_GRID`, `CONNECTOR`
  or `SESSION_DEMAND`.
- *Evidence.* 300 random networks are checked against a brute-force minimum cut, and the CLRS 26.1 network
  gives 23. A mutation that disables reverse-arc updates fails `reverseResidualEdgeReroutesFlow`.

**Baseline.** Greedy priority grants each session `min(connector, demand, headroom)` in index order.

- **Direct model.** The network is a tree, so greedy provably reaches the same total. This is tested on 200 seeded
  problems and now also measured (§5).
- **Feeder model.** Greedy can strand power. In the constructed case it serves 100 kW where Edmonds-Karp serves
  200 kW.

### 3.3 Randomized Quickselect — percentiles (Unit 6, CO5)

```text
lo, hi = 0, n − 1
while lo < hi:
    pivot = A[lo + rng.nextInt(hi − lo + 1)]
    three-way partition A[lo..hi] into  < pivot | = pivot | > pivot,  with the "=" band at [lt, gt]
    if k < lt: hi = lt − 1
    elif k > gt: lo = gt + 1
    else: return pivot
return A[lo]
```

**Randomness and reproducibility.** The generator is injected (`RandomGenerator`) and seeded, so every pivot
sequence can be reproduced. Tests check that the pivot is drawn over the active range, that different seeds take
different paths to the same answer, and that the same seed gives identical counts.

- The percentile index `ceil(p·n) − 1` is computed in exact decimal arithmetic. In IEEE-754 doubles,
  `0.07 × 100 = 7.000000000000001`, which would give the wrong rank.

**Correctness.**

- After partitioning, the `=` band holds exactly the elements whose sorted positions are `[lt, gt]`, so the answer
  is either the pivot or lies wholly in one side.
- Every round removes at least the pivot from the range, so the loop terminates.
- Three-way partitioning keeps all-equal input at one pass. A two-way partition would take `n²/2` comparisons.
- *Evidence.* 50 seeds × random arrays × many `k` are checked against a sorted oracle.

### 3.4 Segment tree — committed load and utilisation (Unit 3)

Each node stores the `(sum, max)` of its range.

- An update rewrites one leaf and re-pulls its ancestors.
- A query splits `[l, r]` into `O(log n)` disjoint canonical nodes and combines them.

**Correctness.**

- By induction on node height, each node holds the aggregate of its range. Sum and max are associative over
  disjoint unions, so a combined query is exact.
- The allocation pipeline relies on this bound: `committed(t) + new(t) ≤ max committed + Σ new ≤ grid` for every
  slot `t`.
- *Evidence.* `queryAfterMultipleUpdates` checks 10,000 seeded operations against the naive scanner.

## 4. Complexity

From [complexity-analysis.md](complexity-analysis.md). Matching graphs have `L` requests, `R` connectors and `E`
edges; flow networks have `V` nodes and `E` edges; arrays have `n` elements.

| Algorithm | Time | Auxiliary space | Baseline (time) |
|---|---|---|---|
| Bipartite matching | `O(V·E)`, precisely `O(L·(R + E))` | `O(L + R)` | Greedy first-available, `O(L + E)` |
| Edmonds-Karp | `O(V·E²)`: at most `O(V·E)` augmentations, each found by one `O(E)` BFS | `O(V + E)` | — |
| Power via Edmonds-Karp | `O(V·E²)` with `V = S + J + 2` direct, or `F + 2S + J + 2` with feeders | `O(V + E)` | Greedy priority, `O(S + J + L)` |
| Randomized Quickselect | expected `O(n)`, worst `O(n²)` with probability vanishing in `n` | `O(1)` in place | Full sort, `O(n log n)` time and `O(n)` copy |
| Segment tree | build `O(n)`, update and query `O(log n)` | `O(n)` (4n nodes) | Naive: update `O(1)`, query `O(n)` |

## 5. Benchmark methodology and results

The full method and every table are in [benchmark-report.md](benchmark-report.md); charts are in
[diagrams/](diagrams/).

**Method.** Three sizes, Small, Medium and Large: 100/30, 1,000/300 and 10,000/3,000 requests per connectors;
30, 300 and 3,000 power sessions; 1,000, 10,000 and 100,000 values. A second dimension varies matching density
(2/8/32), station count (10 or 100) and five value distributions. The algorithm and its baseline get identical
seeded inputs, generated outside the timed region, and both outputs are verified before timing; 3 warm-ups and 7
timed trials per side, reporting median and IQR. One full run uses seed 20261103, with three more seeds for
selection and feeders, and a JMH suite repeats the measurements in a controlled harness. Every case passed
verification with no capacity violation.

**Results (medians, seed 20261103).**

| Comparison | Quality | Time |
|---|---|---|
| Matching vs greedy | Equal, or +1 request (Medium and Large density 2): random graphs rarely hold the structure greedy gets wrong | Large density 32: 1.4 s vs 140 µs. Edge examinations follow `(L − R)·R·d`: every unmatchable request searches the whole alternating tree |
| Edmonds-Karp vs greedy, direct model | **Identical totals in all 6 cases**, as the tree argument predicts | Large: 39 ms vs 23.9 µs |
| Edmonds-Karp vs greedy, feeders | EK ahead in 9 of 24 cases over 4 seeds, by 0.10% to 7.17%; never behind; every Large case tied | Large: 15–18 ms vs about 70 µs |
| Quickselect vs sort | Same value always | Uniform Large: 0.89 ms vs 5.08 ms. Sorted Large: 442 µs vs 75.9 µs, because the JDK sort exploits existing runs |
| Segment tree vs naive scan | Same answers always | JMH, per operation: 39 ns vs 83 ns (Small), 59 ns vs 822 ns (Medium), 90 ns vs 8.02 µs (Large): 2.1×, 14× and 89× faster. The in-application run was noisier for this family (benchmark-report §8) |

**When each algorithm helps.**

- **Matching** serves more requests whenever early requests take connectors that later, more constrained requests
  need. The Algorithm Lab example shows this (2 against 1). On uniformly random demand, greedy is already near-optimal.
- **Edmonds-Karp** helps whenever the network has routing choice, as with shared feeders. On the spec's tree-shaped
  network, its value is the optimality certificate and the bottleneck diagnosis, not extra power.
- **Quickselect** helps on unordered data, which is what waiting times and demand are.
- **The segment tree** was already 2.1× faster than scanning at n = 1,000 under JMH, and the gap grows with `n`.

## 6. Testing

Evidence and commands are in [test-report.md](test-report.md).

| Suite | Result |
|---|---|
| Backend unit tests (Surefire) | 189 passed |
| Backend integration tests (Failsafe, Testcontainers PostgreSQL) | 86 passed |
| Frontend (Vitest) | 60 passed |

The assessed algorithms carry 10 or more tests each, beyond the spec's minimum of five: edge cases (empty, single
element, no edges, zero capacity, all-equal), invalid input (negative capacity, out-of-range indices, `p` outside
`(0, 1]`), stress (10,000-vertex augmenting chains, `Long` extremes) and oracle checks against brute force or sorting
on hundreds of seeded instances.

**Other evidence.**

- Integration tests drive the real REST API through the full allocation pipeline, the outage policy, the error
  contract, simulations (a re-run reproduces the input hash and every non-timing metric) and benchmarks.
- Simulation scenarios report **zero capacity violations** across 8 scenarios × 5 seeds, for both strategies.

## 7. Limitations

- **One new booking per connector per run.** Two non-overlapping new requests for the same connector are served
  across two runs. This is conservative and safe.
- **Peak-based capacity reservation.** A station's available capacity uses the peak committed load over the whole
  batch horizon, which can under-use capacity in quiet slots.
- **Per-station matching.** Each request names one station, so the matching graph splits into per-station
  components. Choosing the station is not part of the optimisation.
- **Edmonds-Karp adds no power on the spec model.** On the spec's tree-shaped power network, it delivers the same
  total as greedy (proved and measured). The shared-feeder extension D12 is the documented case where it delivers
  more, and the measured gain depends on the input.
- **Matching cost at LARGE.** The plain `O(V·E)` matcher takes about 1.4 s at 10,000 requests and density 32.
  Two remedies are recorded as future work, not implemented: an early exit once every connector is matched, and
  Hopcroft-Karp.
- **Physics not modelled.** There is no battery tapering, conversion loss, live hardware, OCPP integration or
  payment.
- **Partly real data and simulated sessions.** Stations come from Open Charge Map and OpenStreetMap and vehicles
  are real models, but connectors the sources do not describe are estimated, grid limits and requests are made up,
  and session progress is simulated. The UI labels simulated sessions; the README lists the source of each field.
- **Coverage follows the open data.** Kerala, Karnataka, Tamil Nadu and Delhi are well listed; Mumbai and
  Hyderabad have few stations in either source, so the demo network is thin there.
- **Wall-clock benchmarks.** The in-application benchmarks are wall-clock timings inside a running server. JMH is
  the controlled measurement.

## 8. Individual contributions

The ownership below is the plan from spec §16. Each student confirms and edits it to match their own commits, as
guidelines §8B requires.

| | Student 1 | Student 2 |
|---|---|---|
| Algorithms | Bipartite matching + greedy baseline; Randomized Quickselect + sorting baseline; percentile convention | Edmonds-Karp, flow network, power allocator + greedy baseline, capacity verifier, feeder extension D12; segment tree + naive scanner |
| Backend | Vehicles, bookings, compatibility-graph builder | Stations, connectors, power-problem builder, allocation service, simulation |
| Frontend | Driver portal | Operator console, analytics, Algorithm Lab, simulations, benchmarks |
| Evidence | Matching and selection tests and benchmarks | Flow and tree tests and benchmarks, simulation scenarios |
| Shared | Architecture and schema review, integration tests, benchmark method, this report, viva preparation | Same |

## 9. Screenshots

Screenshots of every screen, taken from the running application at 1440 px and 390 px, are in
[screenshots/](screenshots/) and shown in the README.
