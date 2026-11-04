# CHARGEWISE --- PROJECT SPECIFICATION

**Project:** ChargeWise --- Intelligent EV Charging Allocation & Network
Optimization Platform\
**Course:** Advanced Algorithm --- CIA 3, Semester V, Academic Year
2026--27\
**Team size:** 2 students\
**Primary users:** EV drivers and charging-network operators\
**Technology:** Java 21, Spring Boot, React, TypeScript, PostgreSQL\
**Design direction:** Premium dark SaaS --- obsidian black, charcoal and
electric lime

------------------------------------------------------------------------

## 1. Executive summary

ChargeWise is a web-based platform that connects EV drivers with
charging infrastructure and gives charging-network operators tools to
manage stations, connectors, charging requests, queues and electrical
capacity.

The project addresses a practical operational problem: charging networks
have finite connectors and electrical capacity, while vehicle demand
varies by location and time. Connector compatibility, station
availability and power limits constrain the assignments that can be
made.

ChargeWise will provide two connected experiences:

1.  **Driver portal:** vehicle profiles, station discovery, connector
    availability, charging requests, reservations and session history.
2.  **Operator console:** station and connector management, allocation
    execution, power optimization, congestion analytics, scenario
    simulation and benchmark reporting.

The academic core is a set of self-implemented Java algorithms.
Algorithms must materially determine application outputs; they must not
be decorative demonstrations or hidden library calls.

## 2. Problem statement

EV drivers can encounter unavailable or incompatible connectors and
uncertain waiting times. Charging-network operators must coordinate a
finite number of connectors and respect station-level electrical import
limits. During peak demand or equipment failures, operators need to
understand which requests can be served, how power can be distributed,
and where congestion is emerging.

ChargeWise models these constraints and provides algorithm-driven
decisions. The prototype uses realistic synthetic data and simulates
charging operations; it does not control physical chargers or make
claims about live hardware availability.

## 3. Goals and measurable success criteria

### Functional goals

-   Register drivers and their vehicles.
-   Record vehicle connector compatibility, battery capacity, state of
    charge and maximum charging power.
-   Register stations, grid-import limits and connectors.
-   Accept charging requests with requested energy and time windows.
-   Assign compatible requests to connectors without double-booking.
-   Allocate power without exceeding station or connector constraints.
-   Calculate waiting-time and demand percentiles.
-   Simulate demand spikes, connector failures, station outages and
    grid-capacity reductions.
-   Persist scenario inputs and outputs for reproducibility.

### Measurable criteria

The final report must calculate, not invent, these metrics:

-   Number and percentage of requests assigned.
-   Number of unmatched requests and reasons.
-   Total requested power versus allocated power.
-   Station and connector capacity violations (target: zero).
-   Station utilization.
-   Queue length and waiting-time distribution.
-   Median and 90th-percentile waiting time.
-   Algorithm runtime, operation counts and dataset size.
-   Baseline-versus-algorithm output quality.
-   Reproducibility across fixed benchmark seeds.

Do not promise that the algorithm always minimizes waiting time. Maximum
matching maximizes the number of valid assignments in the modeled graph;
maximum flow maximizes feasible flow in the modeled network. These
objectives are not automatically equivalent to minimum waiting time or
fairness.

## 4. Scope and assumptions

### In scope

-   Web application with driver and operator roles.
-   Synthetic but realistic station, connector, vehicle and request
    datasets.
-   Custom Java implementations of the assessed algorithms.
-   REST APIs and PostgreSQL persistence.
-   Driver booking workflow and operator allocation workflow.
-   Discrete time-slot model for reservations.
-   Simulation and benchmark modules.
-   Unit, integration and edge-case tests.
-   Technical report, README and reproducible setup.

### Out of scope for the CIA prototype

-   Direct control of physical charging equipment.
-   Payment processing.
-   Guaranteed live station data.
-   Production-grade OCPP integration.
-   Certified electrical-grid control.
-   Real-world routing optimization.
-   Clinical, safety-critical or utility-control decisions.

### Modeling assumptions

-   Connector compatibility is represented by explicit connector-type
    identifiers and validated rules.
-   Each connector can serve at most one vehicle in a discrete time
    slot.
-   Each station has a grid-import power limit.
-   Each connector has a maximum output power.
-   Each vehicle has a maximum accepted charging power.
-   A charging request has a requested energy amount and a time window.
-   The prototype uses a simplified constant-power model during an
    allocation interval. Battery tapering and conversion losses are not
    modeled unless added as a documented extension.
-   All power values use kW and energy values use kWh.
-   Timestamps are stored in UTC.
-   Scenario data and random seeds are saved for reproducibility.

## 5. Users and workflows

### Driver workflow

1.  Register or sign in.
2.  Add a vehicle with battery capacity, state of charge, maximum
    charging power and connector compatibility.
3.  Browse stations and inspect connector status.
4.  Enter requested energy and a preferred time window.
5.  Submit a charging request.
6.  Receive an assignment, waitlist status or clear rejection reason.
7.  View simulated session progress and charging history.

### Operator workflow

1.  Sign in with operator role.
2.  View network overview and station map.
3.  Add or update stations and connectors.
4.  Inspect pending requests and active sessions.
5.  Run the matching and power-allocation pipeline.
6.  Review assignments, unmatched requests, power allocation and
    bottlenecks.
7.  Run what-if scenarios such as connector outage or reduced grid
    capacity.
8.  Compare algorithm outputs with baselines and inspect benchmark
    results.

## 6. Algorithm specifications

All assessed algorithms must be implemented by the team in Java. Java
collections may be used as supporting data structures, but not as a
substitute for the assessed algorithm. Do not call graph, matching,
selection or segment-tree libraries to perform the assessed work.

### 6.1 Bipartite matching --- core algorithm, Unit 5

**Purpose:** Assign charging requests to compatible connectors for a
particular discrete time slot.

Model a bipartite graph `G = (U, V, E)`:

-   `U`: eligible EV charging requests.
-   `V`: available connectors.
-   `(u, v) ∈ E`: request `u` can use connector `v` based on connector
    compatibility, connector status, time-window overlap, vehicle power
    requirements and station rules.

Implement augmenting-path matching using DFS:

1.  Build the compatibility graph.
2.  Initialize every connector as unmatched.
3.  Visit each request and search for an augmenting path.
4.  Reassign a previously matched request when an alternate valid
    connector is found.
5.  Return the matching and unmatched requests.

**Expected complexity:** `O(VE)` for the standard DFS-based
augmenting-path implementation, where `V` and `E` describe the
constructed bipartite graph.

**Output:** assignments, unmatched requests, matching cardinality and
reasons a request had no eligible edge.

**Baseline:** greedy first-available assignment in a fixed request
order. Compare cardinality and runtime. Include a constructed case where
greedy performs worse than maximum matching.

**Important:** Matching is performed per time slot or over a
conflict-expanded graph. A connector must never be assigned to
overlapping bookings.

### 6.2 Edmonds-Karp maximum flow --- core algorithm, Unit 5

**Purpose:** Allocate feasible electrical power to matched charging
sessions while respecting station, connector and vehicle limits.

Use a layered flow network:

-   Source → station nodes: capacity equals station grid-import capacity
    available to the allocation interval.
-   Station → session nodes: include an edge only when the session has
    been assigned to a connector at that station; capacity is bounded by
    the assigned connector's maximum power and any station-to-session
    compatibility constraint.
-   Session → sink: capacity equals the session's requested power,
    bounded by the vehicle's maximum accepted power.

If several sessions share one station, the source-to-station edge
ensures aggregate power does not exceed the station limit. The
station-to-session edge ensures the individual connector limit is
respected. Session-to-sink limits the vehicle/request demand.

Implement Edmonds-Karp from scratch:

1.  Build residual capacity and flow structures.
2.  Repeatedly find an augmenting path using BFS.
3.  Find the minimum residual capacity on the path.
4.  Update forward and reverse residual edges.
5.  Stop when no augmenting path exists.
6.  Return total flow and per-edge/session allocations.

**Expected complexity:** `O(VE²)`.

**Output:** total allocated kW, station-wise allocation, session-wise
allocation, unserved requested kW, and residual bottleneck information.

**Baseline:** a documented greedy power allocator, such as serving
sessions in a fixed priority order until each station's available
capacity is exhausted. Compare total feasible power, unmet demand,
constraint violations and runtime.

**Important:** Maximum flow maximizes total feasible flow in the modeled
network. It does not inherently maximize the number of fully served
vehicles, minimize waiting time or guarantee fairness. State the
objective precisely. If fairness is added, implement and document a
separate policy rather than attributing it to Edmonds-Karp.

### 6.3 Randomized Quickselect --- core algorithm, Unit 6, CO5

**Purpose:** Calculate waiting-time or demand percentiles without fully
sorting every observation.

Implement in-place randomized Quickselect:

1.  Validate the input and requested zero-based order statistic `k`.
2.  Choose a pivot using a seeded random-number generator.
3.  Partition the active range.
4.  Continue only in the partition containing index `k`.
5.  Return the selected order statistic.

Use a reproducible seed in benchmark and test modes. Production
simulation can use a generated seed that is stored with the scenario.

**Expected complexity:** `O(n)`; worst case `O(n²)`. Iterative in-place
implementation can use `O(1)` auxiliary space, excluding input storage.

**Output:** median, 75th percentile and 90th percentile waiting times or
demand scores, depending on the selected metric.

**Baseline:** full sorting followed by order-statistic extraction.
Define percentile indexing precisely, for example nearest-rank:
`k = ceil(p*n) - 1` for `0 < p <= 1`. Use the same convention for both
implementations.

**CO5 evidence:** genuine randomized pivot selection, recorded seeds,
correctness across multiple seeds, performance over multiple input
distributions, and discussion of expected and worst-case behavior.

Quickselect supports analytics and threshold reporting. It does not
itself optimize matching or power allocation.

### 6.4 Segment Tree --- additional technique, Unit 3

**Purpose:** Maintain range aggregates over discretized station
utilization or availability time slots.

Implement a segment tree for a clearly defined aggregate, initially
range sum or range maximum over utilization values:

-   Build: `O(n)`.
-   Point update: `O(log n)`.
-   Range query: `O(log n)`.

Compare range queries and updates against a naive array scan. Document
indexing conventions and invalid-range behavior.

## 7. End-to-end allocation pipeline

1.  Load pending requests and station/connector state for the selected
    allocation interval.
2.  Validate request data and determine eligible requests.
3.  Use Quickselect to calculate network congestion/demand percentiles
    for operator analytics.
4.  Build the compatibility graph and run bipartite matching.
5.  For matched requests, build the station/session power-flow network
    and run Edmonds-Karp.
6.  Persist connector assignments and power allocations transactionally.
7.  Update station utilization/availability aggregates using the Segment
    Tree in the analytics module.
8.  Return a result containing algorithm versions, scenario ID,
    assignments, power allocation, unmatched requests and metrics.

The allocation service must handle failure safely: if persistence fails,
do not leave partial bookings or connector assignments.

## 8. Technical architecture

### Stack

-   Java 21
-   Spring Boot
-   Maven
-   PostgreSQL
-   Spring Data JPA
-   Flyway database migrations
-   React + TypeScript + Vite
-   Tailwind CSS
-   Recharts
-   Leaflet with OpenStreetMap tiles
-   JUnit 5 and Mockito
-   JMH for Java microbenchmarks where practical
-   Docker Compose for local PostgreSQL and optional application
    services
-   Git and GitHub

### Backend layers

-   Controller: HTTP request/response and authentication boundaries.
-   DTO: validated API input/output types.
-   Service: business workflows and transaction boundaries.
-   Algorithm: independent, framework-agnostic Java implementations.
-   Repository: persistence.
-   Entity: relational domain model.
-   Simulation: deterministic scenario generation and execution.
-   Benchmark: repeatable performance experiments.
-   Exception: consistent API error handling.

Algorithm classes must be callable directly from unit tests without
starting Spring.

### Frontend areas

-   Public/driver: sign-in, vehicle garage, station discovery, station
    details, booking, session tracking and history.
-   Operator: overview, network map, stations, connectors, requests,
    allocation results, analytics, simulation laboratory and benchmarks.

### Security

-   Hash passwords using a suitable password-hashing implementation;
    never store plaintext passwords.
-   Enforce role-based access: drivers can access their own
    vehicles/bookings; operators can manage network resources.
-   Validate all inputs on the server.
-   Do not commit secrets. Use environment variables and provide
    `.env.example` with placeholders only.

## 9. Database schema

Use PostgreSQL. Suggested tables:

### users

-   `id BIGSERIAL PRIMARY KEY`
-   `name VARCHAR(120) NOT NULL`
-   `email VARCHAR(255) UNIQUE NOT NULL`
-   `password_hash VARCHAR(255) NOT NULL`
-   `role VARCHAR(20) NOT NULL`
-   `created_at TIMESTAMPTZ NOT NULL`

### vehicles

-   `id BIGSERIAL PRIMARY KEY`
-   `user_id BIGINT REFERENCES users(id)`
-   `registration_number VARCHAR(40) UNIQUE NOT NULL`
-   `battery_capacity_kwh NUMERIC(8,2) NOT NULL`
-   `current_soc NUMERIC(5,2) NOT NULL`
-   `max_charging_kw NUMERIC(8,2) NOT NULL`
-   `created_at TIMESTAMPTZ NOT NULL`

### vehicle_connector_types

-   `vehicle_id BIGINT REFERENCES vehicles(id)`
-   `connector_type VARCHAR(40) NOT NULL`
-   composite primary key `(vehicle_id, connector_type)`

### stations

-   `id BIGSERIAL PRIMARY KEY`
-   `name VARCHAR(160) NOT NULL`
-   `address TEXT NOT NULL`
-   `latitude NUMERIC(10,7)`
-   `longitude NUMERIC(10,7)`
-   `grid_capacity_kw NUMERIC(10,2) NOT NULL`
-   `status VARCHAR(24) NOT NULL`
-   `created_at TIMESTAMPTZ NOT NULL`

### connectors

-   `id BIGSERIAL PRIMARY KEY`
-   `station_id BIGINT REFERENCES stations(id)`
-   `connector_type VARCHAR(40) NOT NULL`
-   `max_power_kw NUMERIC(8,2) NOT NULL`
-   `status VARCHAR(24) NOT NULL`

### bookings

-   `id BIGSERIAL PRIMARY KEY`
-   `user_id BIGINT REFERENCES users(id)`
-   `vehicle_id BIGINT REFERENCES vehicles(id)`
-   `station_id BIGINT REFERENCES stations(id)`
-   `connector_id BIGINT REFERENCES connectors(id)` nullable until
    assigned
-   `requested_energy_kwh NUMERIC(8,2) NOT NULL`
-   `requested_start TIMESTAMPTZ NOT NULL`
-   `requested_end TIMESTAMPTZ NOT NULL`
-   `status VARCHAR(24) NOT NULL`
-   `created_at TIMESTAMPTZ NOT NULL`

### charging_sessions

-   `id BIGSERIAL PRIMARY KEY`
-   `booking_id BIGINT UNIQUE REFERENCES bookings(id)`
-   `station_id BIGINT REFERENCES stations(id)`
-   `connector_id BIGINT REFERENCES connectors(id)`
-   `start_time TIMESTAMPTZ`
-   `end_time TIMESTAMPTZ`
-   `energy_delivered_kwh NUMERIC(10,3) NOT NULL DEFAULT 0`
-   `allocated_power_kw NUMERIC(8,2) NOT NULL DEFAULT 0`
-   `status VARCHAR(24) NOT NULL`

### power_allocations

-   `id BIGSERIAL PRIMARY KEY`
-   `session_id BIGINT REFERENCES charging_sessions(id)`
-   `requested_kw NUMERIC(8,2) NOT NULL`
-   `allocated_kw NUMERIC(8,2) NOT NULL`
-   `allocation_time TIMESTAMPTZ NOT NULL`

### simulations

-   `id BIGSERIAL PRIMARY KEY`
-   `created_by BIGINT REFERENCES users(id)`
-   `name VARCHAR(160) NOT NULL`
-   `random_seed BIGINT NOT NULL`
-   `configuration JSONB NOT NULL`
-   `status VARCHAR(24) NOT NULL`
-   `created_at TIMESTAMPTZ NOT NULL`

### simulation_results

-   `id BIGSERIAL PRIMARY KEY`
-   `simulation_id BIGINT REFERENCES simulations(id)`
-   `algorithm_name VARCHAR(80) NOT NULL`
-   `algorithm_version VARCHAR(40) NOT NULL`
-   `input_summary JSONB NOT NULL`
-   `output_summary JSONB NOT NULL`
-   `metrics JSONB NOT NULL`
-   `runtime_ns BIGINT`
-   `created_at TIMESTAMPTZ NOT NULL`

Add indexes for station status, connector station/status, booking
time/status, session station/status and simulation ID. Use database
transactions and appropriate uniqueness/exclusion safeguards to prevent
overlapping connector reservations. If PostgreSQL exclusion constraints
are used, document the required range representation and migration.

## 10. REST API contract

Base path: `/api/v1`

### Authentication

-   `POST /auth/register`
-   `POST /auth/login`
-   `GET /auth/me`

### Driver

-   `GET /stations`
-   `GET /stations/nearby`
-   `GET /stations/{id}`
-   `GET /stations/{id}/availability`
-   `POST /vehicles`
-   `GET /vehicles`
-   `PUT /vehicles/{id}`
-   `POST /bookings`
-   `GET /bookings`
-   `GET /bookings/{id}`
-   `PATCH /bookings/{id}/cancel`
-   `GET /sessions/{id}`
-   `GET /sessions/history`

### Operator

-   `GET /operator/dashboard`
-   `POST /operator/stations`
-   `PUT /operator/stations/{id}`
-   `POST /operator/stations/{id}/connectors`
-   `PATCH /operator/connectors/{id}/status`
-   `GET /operator/sessions`
-   `GET /operator/requests`

### Algorithms and allocation

-   `POST /operator/allocation/run`
-   `GET /operator/allocation/{id}`
-   `POST /algorithms/matching/execute`
-   `POST /algorithms/max-flow/execute`
-   `POST /algorithms/quickselect/execute`
-   `POST /algorithms/segment-tree/query`

### Simulation and benchmarks

-   `POST /simulations`
-   `POST /simulations/{id}/run`
-   `GET /simulations/{id}/results`
-   `GET /simulations/{id}/comparison`
-   `POST /benchmarks/run`
-   `GET /benchmarks/{id}`

Use DTOs and Bean Validation. Return consistent errors with HTTP status,
error code, message and field-level validation details. Do not expose
entity objects directly.

### Example booking request

``` json
{
  "vehicleId": 101,
  "stationId": 12,
  "requestedEnergyKwh": 24,
  "requestedStart": "2026-11-05T10:00:00Z",
  "requestedEnd": "2026-11-05T11:00:00Z"
}
```

### Example matching result

``` json
{
  "algorithm": "BIPARTITE_MATCHING",
  "totalRequests": 20,
  "matchedRequests": 17,
  "unmatchedRequests": 3,
  "assignments": [
    {
      "vehicleId": 101,
      "connectorId": 5012,
      "status": "ASSIGNED"
    }
  ]
}
```

### Example power result

``` json
{
  "algorithm": "EDMONDS_KARP",
  "totalRequestedKw": 300.0,
  "totalAllocatedKw": 240.0,
  "unservedKw": 60.0,
  "stationAllocations": [],
  "sessionAllocations": []
}
```

## 11. Repository structure

``` text
chargewise/
├── README.md
├── PROJECT_SPEC.md
├── docker-compose.yml
├── .env.example
├── docs/
│   ├── project-proposal.md
│   ├── architecture.md
│   ├── algorithms.md
│   ├── database-schema.md
│   ├── api-documentation.md
│   ├── complexity-analysis.md
│   ├── benchmark-report.md
│   ├── test-report.md
│   └── diagrams/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/chargewise/
│       │   ├── ChargeWiseApplication.java
│       │   ├── config/
│       │   ├── security/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── entity/
│       │   ├── exception/
│       │   ├── repository/
│       │   ├── service/
│       │   ├── algorithm/
│       │   │   ├── matching/BipartiteMatcher.java
│       │   │   ├── flow/FlowNetwork.java
│       │   │   ├── flow/EdmondsKarp.java
│       │   │   ├── selection/RandomizedQuickselect.java
│       │   │   └── tree/ChargingAvailabilitySegmentTree.java
│       │   ├── simulation/
│       │   ├── analytics/
│       │   └── benchmark/
│       └── test/java/com/chargewise/
│           ├── algorithm/
│           ├── service/
│           ├── controller/
│           └── benchmark/
├── frontend/
│   ├── package.json
│   └── src/
│       ├── components/
│       ├── layouts/
│       ├── pages/driver/
│       ├── pages/operator/
│       ├── features/
│       ├── services/
│       ├── hooks/
│       ├── types/
│       └── utils/
├── datasets/
│   ├── stations.json
│   ├── connectors.json
│   ├── vehicles.json
│   ├── charging_requests.json
│   └── benchmark/
└── scripts/
    └── generate_dataset.py
```

## 12. Testing requirements

Provide at least six tests per core algorithm, including boundary and
invalid-input cases.

### Bipartite matching

1.  One request and one compatible connector.
2.  Multiple requests with multiple connectors.
3.  No compatible connectors.
4.  More requests than connectors.
5.  Multiple requests competing for one connector.
6.  Empty graph.
7.  Include a case where augmenting-path reassignment beats a greedy
    first-choice assignment.

### Edmonds-Karp

1.  Single source-to-sink path.
2.  Multiple augmenting paths.
3.  Bottleneck edge.
4.  No source-to-sink path.
5.  Zero-capacity edges.
6.  Disconnected/empty network.
7.  Reverse residual edge case that requires rerouting flow.

### Randomized Quickselect

1.  Single element.
2.  Sorted input.
3.  Reverse-sorted input.
4.  Duplicate values.
5.  All values equal.
6.  Invalid `k` and empty input.
7.  Repeat with multiple seeds and compare against sorted reference
    output.

### Segment Tree

1.  Build and full-range query.
2.  Single-element query.
3.  Partial-range query.
4.  Point update.
5.  Query after multiple updates.
6.  Invalid and empty ranges.

### Integration and system tests

-   Driver creates a vehicle and submits a valid request.
-   Incompatible connector is never assigned.
-   Two requests cannot reserve the same connector in overlapping
    intervals.
-   Station outage invalidates or reassigns affected reservations
    according to documented policy.
-   Grid-capacity reduction never produces a capacity violation.
-   Driver can only access their own bookings and vehicles.
-   Operator can execute a simulation and retrieve persisted results.
-   Database failure does not leave partial allocations.
-   Invalid, duplicate and empty datasets return clear errors.

## 13. Benchmarking plan

Use three or more increasing input sizes. Proposed initial sizes:

  Scale      Requests   Connectors
  -------- ---------- ------------
  Small           100           30
  Medium        1,000          300
  Large        10,000        3,000

Vary graph density and station/session counts independently; a single
size dimension is not sufficient for graph algorithms.

### Comparisons

-   Bipartite matching vs greedy first-available allocation.
-   Edmonds-Karp vs documented greedy power allocation.
-   Randomized Quickselect vs full sorting.
-   Segment Tree vs naive range scanning.

### Metrics

-   Runtime in nanoseconds or milliseconds.
-   Median runtime over repeated runs.
-   Number of comparisons, BFS traversals or augmentations where
    applicable.
-   Matching cardinality.
-   Total allocated power.
-   Unserved demand.
-   Constraint violations.
-   Query/update latency for Segment Tree.
-   Memory use where feasible.

### Methodology

-   Generate datasets with recorded seeds.
-   Keep data generation outside the timed region.
-   Warm up JVM code.
-   Use repeated runs and report median and spread.
-   Use JMH where practical for microbenchmarks.
-   Ensure each baseline receives exactly the same input.
-   Validate output correctness before comparing runtime.
-   Include theoretical time/space complexity.
-   Never invent benchmark results or claim improvements before
    measurement.

## 14. UI/UX requirements

### Design tokens

-   Background: `#090B0A`
-   Surface: `#171B19`
-   Accent: `#B8F34A`
-   Muted text: `#8B9690`
-   Primary text: near-white
-   Use restrained electric-lime highlights, clear hierarchy, accessible
    contrast and consistent spacing.

### Operator dashboard

-   KPI cards: allocated power, available connectors, median waiting
    time, station utilization.
-   Map of stations with status and capacity.
-   Active session table.
-   Pending request queue.
-   Allocation execution controls.
-   Power allocation visualization.
-   Congestion and percentile charts.
-   Scenario simulator and benchmark laboratory.

### Driver portal

-   Vehicle garage.
-   Station map and list.
-   Connector compatibility and availability.
-   Requested energy and time-window form.
-   Booking status and session details.
-   Charging history.

Use loading, empty, success and error states. Do not show fabricated
live metrics. Clearly label simulated data.

## 15. Six-week implementation milestones

### Week 1 --- Foundation

-   Confirm proposal and acceptance criteria.
-   Create repository and project skeleton.
-   Configure Spring Boot, React and PostgreSQL.
-   Create initial schema and sample data.
-   Build dashboard shell.

### Week 2 --- Algorithms

-   Implement matching, Edmonds-Karp and Quickselect.
-   Implement Segment Tree.
-   Write unit tests and verify manually constructed examples.
-   Document pseudocode and complexity.

### Week 3 --- Backend

-   Build entities, repositories and services.
-   Implement validation and REST APIs.
-   Integrate matching and power flow.
-   Add transaction-safe booking/allocation behavior.

### Week 4 --- Frontend

-   Build driver and operator interfaces.
-   Integrate station discovery and booking.
-   Integrate allocation results and visualizations.
-   Add responsive and accessible states.

### Week 5 --- Simulation and evidence

-   Add outage, peak-demand and grid-reduction scenarios.
-   Implement baselines and benchmark runner.
-   Run correctness, integration and stress tests.
-   Generate charts from measured outputs.

### Week 6 --- Submission

-   Fix defects and verify reproducibility.
-   Finish proposal, report and README.
-   Capture screenshots and demonstration evidence.
-   Prepare viva notes and contribution records.

## 16. Two-student work allocation

### Student 1 --- Matching, Quickselect and driver experience

-   Own BipartiteMatcher and RandomizedQuickselect.
-   Implement vehicle and booking APIs with shared review.
-   Build driver portal.
-   Own matching/selection tests and benchmark comparisons.
-   Document these algorithms and their complexity.

### Student 2 --- Flow, Segment Tree and operator experience

-   Own EdmondsKarp and ChargingAvailabilitySegmentTree.
-   Implement station, connector, power and simulation services with
    shared review.
-   Build operator console.
-   Own flow/tree tests and benchmark comparisons.
-   Document these algorithms and their complexity.

### Shared responsibilities

-   Architecture and database review.
-   Integration and code review.
-   End-to-end testing.
-   Benchmark methodology.
-   Technical report and viva preparation.

Both students must understand all algorithms and the end-to-end
pipeline.

## 17. Eight-minute demonstration plan

1.  **0:00--1:00:** Explain EV charging congestion and show the network.
2.  **1:00--2:00:** Demonstrate driver vehicle setup and charging
    request.
3.  **2:00--3:00:** Run bipartite matching and show
    assignments/unmatched requests.
4.  **3:00--4:00:** Run Edmonds-Karp and show power allocation and
    residual bottlenecks.
5.  **4:00--5:00:** Run Quickselect and show the 90th-percentile waiting
    time.
6.  **5:00--6:00:** Take stations offline and reduce grid capacity;
    rerun the pipeline.
7.  **6:00--7:00:** Show benchmark comparisons and measured performance.
8.  **7:00--8:00:** Summarize operational results, constraints and
    limitations.

## 18. Required submission artifacts

-   Project proposal (1--2 pages).
-   Complete source repository with readable Git history and
    contribution attribution.
-   Technical report (5--8 pages).
-   Working local or hosted application.
-   Sample datasets and representative outputs.
-   Correctness tests and benchmark results.
-   README with prerequisites, setup commands, environment variables,
    API examples and sample data.
-   Screenshots and demonstration evidence.
-   Individual contribution summary and viva preparation.

## 19. Definition of done

ChargeWise is ready for submission only when:

-   [ ] Driver and operator workflows work end-to-end.
-   [ ] Java backend executes all assessed algorithms.
-   [ ] At least two distinct syllabus algorithms are central to the
    application.
-   [ ] A Unit 4--6 technique is included.
-   [ ] CO5 randomized/probabilistic component is implemented and
    evidenced.
-   [ ] All algorithm tests pass, including edge cases.
-   [ ] No connector or station power capacity constraints are violated.
-   [ ] Baselines are implemented and compared on identical inputs.
-   [ ] Three or more increasing benchmark sizes are tested.
-   [ ] Complexity analysis is documented.
-   [ ] Invalid and duplicate inputs are handled.
-   [ ] Simulation results can be reproduced from saved configuration
    and seed.
-   [ ] README setup instructions work on a clean environment.
-   [ ] Both students can explain correctness, complexity and their
    contributions.

------------------------------------------------------------------------

**Academic integrity note:** The team must be able to explain, verify
and defend every assessed algorithm. AI tools may assist with
scaffolding and review, but generated code must be inspected, tested and
understood by the students.
