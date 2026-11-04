# ChargeWise — Project Proposal

**Title:** ChargeWise: Intelligent EV Charging Allocation and Network Optimization Platform
**Course:** Advanced Algorithm, CIA 3, Semester V, B.Tech CS AI & ML, 2026–27

| Team member | ID | Role |
|---|---|---|
| Student 1 *(name to be filled in)* | *(ID)* | Bipartite matching, Randomized Quickselect, driver portal, vehicle and booking workflows |
| Student 2 *(name to be filled in)* | *(ID)* | Edmonds-Karp, segment tree, operator console, power and simulation workflows |

## 1. Real-world problem

A public EV charging network has a fixed number of connectors, and each station can import only a limited amount of
power from the grid. At peak times demand exceeds supply. Drivers find connectors busy, offline or of the wrong type;
operators must decide which request gets which connector, then split each station's power between its vehicles
without exceeding any station, connector or vehicle limit, and redo it all when a station fails or the grid limit is
cut. First-come-first-served wastes capacity: an early request can take the only connector a later vehicle could
use, and a fixed-order power split can leave usable power on the table.

## 2. Target users

**EV drivers** register vehicles, find compatible stations and request a charging window. **Charging-network
operators** manage stations, run allocation, react to outages and grid cuts, and study congestion.

## 3. Proposed features

- **Driver portal:** vehicle garage, station map with live connector compatibility, charging requests with an energy
  and time window, and the reason for every outcome.
- **Operator console:** network overview, one-click allocation with full algorithm evidence, outage and grid-limit
  controls, congestion and percentile analytics.
- **Algorithm Lab** (each algorithm against its baseline on a worked example, seeded instance or uploaded JSON),
  seeded **scenario simulations** and **benchmarks** at three sizes.

## 4. Core techniques and why each fits

| Technique (syllabus unit) | Role in ChargeWise | Why it is the right tool |
|---|---|---|
| **Bipartite matching** by DFS augmenting paths (Unit 5) | Assigns charging requests to compatible, free connectors | Requests and connectors form a bipartite compatibility graph. Maximum matching serves the most requests: an augmenting path can move an earlier request so a later one fits, which first-come greedy cannot. |
| **Edmonds-Karp maximum flow** (Unit 5) | Splits each station's grid power between its sessions | The source → station → session → sink network encodes every limit as a capacity, so max-flow gives the most power with no violation, and the min-cut names the binding limits. |
| **Randomized Quickselect** (Unit 6, CO5) | Median and 90th-percentile waiting time and demand | Finds a percentile in expected `O(n)` without sorting; a seeded random pivot makes that bound hold on every input, sorted or duplicate-heavy included. |
| **Segment tree** (Unit 3) | Committed load per time slot for capacity reservation and range analytics | Range-sum and range-max over the slot timeline in `O(log n)` while bookings change it, instead of an `O(n)` scan. |

## 5. Data source

Real data wherever a public source exists, realistic made-up data where none does:

- **1,971 real stations in 643 cities across India**: every public car charging station in Open Charge Map's export
  (CC BY 4.0) or on OpenStreetMap (ODbL 1.0), with names, coordinates, addresses and status; city and state from
  GeoNames (CC BY 4.0).
- **4,614 connectors** of five real standards (CCS2, Type 2 AC, CHAdeMO, Bharat DC-001, Bharat AC-001), from the
  sources where listed, otherwise estimated from each operator's typical hardware.
- **134 vehicles of real Indian EV models** with published specs, owned by 100 made-up drivers.
- **Made up, because no public source exists:** 600 charging requests, and each station's grid limit (60–100 % of
  its charger total).

`scripts/generate_dataset.py --seed 20261102` regenerates it byte-for-byte; simulations use recorded seeds.

## 6. Measurable success criteria

Every criterion is computed by the application or its tests, never estimated. Matching maximizes the **number** of
valid assignments and max-flow the **total** deliverable power; neither is claimed to minimize waiting or be fair.

| Criterion | Target |
|---|---|
| Capacity violations (station, connector, vehicle) | **Zero** in every test, scenario and benchmark, checked by an independent verifier |
| Assigned and unmatched requests (each with a reason); allocated against requested power; p50 and p90 waiting time | Reported for every allocation run and simulation |
| Matching and Edmonds-Karp against their greedy baselines | Never fewer requests, never less power, on the same input |
| Correctness tests | At least five per core algorithm, including boundary and invalid input |
| Benchmarks | Three sizes (100/1,000/10,000 requests), varied density, median and spread over repeated trials |
| Reproducibility | A saved scenario re-run with its seed reproduces every non-timing metric |
