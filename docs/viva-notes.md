# Viva preparation and contribution summary

Guidelines §10: each student has about two minutes to explain their contribution, a key algorithm, its correctness
and its complexity. The examiner may change the input, ask for an edge case, or ask for a walkthrough of a Java
method. Both students must be able to answer for **all four** algorithms and the allocation pipeline (spec §16).

Use these notes to rehearse; do not read them aloud. Every figure here is in the linked documents. If a figure is
quoted, be ready to show where it comes from.

## Contribution summary

The ownership follows spec §16. Each student edits their own row to match what they actually built, and the Git
history should show it (guidelines §8B).

| | Student 1 *(name, ID)* | Student 2 *(name, ID)* |
|---|---|---|
| Owned algorithms | `BipartiteMatcher`, `GreedyFirstAvailableMatcher`, `MatchingVerifier`; `RandomizedQuickselect`, `SortingSelector`, `Percentiles` | `EdmondsKarp`, `FlowNetwork`, `MaxFlowPowerAllocator`, `GreedyPowerAllocator`, `CapacityVerifier`, feeder extension D12; `ChargingAvailabilitySegmentTree`, `NaiveRangeScanner` |
| Backend | Vehicles and bookings API, compatibility-graph builder | Stations and connectors API, power-problem builder, allocation service, outage policy, simulation |
| Frontend | Driver portal: garage, find a station, booking, requests | Operator console: overview, allocation, stations, analytics, Algorithm Lab, simulations, benchmarks |
| Tests and benchmarks | Matching and selection tests; `MATCHING` and `SELECTION` benchmark families | Flow and tree tests; `POWER`, `POWER_FEEDERS` and `RANGE_QUERY` families; simulation scenarios |
| Shared | Architecture and schema review, integration tests, benchmark method, report, demo | Same |

## Two-minute outline (each student)

1. **Contribution (20 s).** "I built X, Y and Z. My commits are on these branches."
2. **Algorithm (40 s).** What problem it solves in ChargeWise, and the core loop.
3. **Correctness (30 s).** The invariant and the theorem (below).
4. **Complexity (20 s).** Time and space, and what the benchmarks measured.
5. **One honest limitation (10 s).**

---

## Student 1 — Bipartite matching

**In one sentence.** Requests are left vertices, connectors are right vertices, and an edge means "this connector
can serve this request". Kuhn's algorithm finds a maximum matching by searching for augmenting paths.

**Method walkthrough.** `BipartiteMatcher.match` → `Run.execute` → `augmentFrom`:

- `execute` loops over requests, and bumps `stamp` to clear the visited marks in O(1).
- `augmentFrom` is a DFS over an **explicit stack**:
  - `stackLeft[k]` is the request at depth `k`
  - `nextNeighbor[k]` is the next connector it will try
  - `viaRight[k]` is the connector it descended through
- On finding a free connector, `flipPath` reassigns every request on the path in one loop.
- A reassignment is counted when `depth > 0`, which means an already-matched request was moved.

**Why an explicit stack?** An augmenting path can be thousands of edges long. `longAugmentingPathDoesNotOverflowStack`
runs a 10,000-vertex chain.

**Correctness.**

- **Berge's theorem.** A matching is maximum if and only if no augmenting path exists.
- **Single pass is enough.** If the search from `u` fails, no later augmentation can create a path for `u`, because
  augmentations only move requests along alternating paths. So one pass over the requests suffices.
- **Validity.** Flipping an alternating path keeps every vertex matched at most once. `MatchingVerifier` checks this
  independently of the algorithm.
- **Evidence.** `randomGraphsMatchBruteForceMaximum` compares 200 random graphs with exhaustive search.

**Complexity.**

- `O(L·(R + E))` time, which is `O(V·E)`: each of `L` searches is `O(R + E)`.
- `O(L + R)` space.
- **Measured.** At density 8 and 32, edge examinations follow `(L − R)·R·d`. Each request that cannot be matched
  explores the whole alternating tree, which is why Large density 32 takes 1.4 s
  ([benchmark-report.md §3](benchmark-report.md)).

**Likely questions.**

- *Why not just greedy?*
  - Greedy never revisits a choice. Two requests: R1 fits C1 or C2, R2 fits only C1. Greedy gives C1 to R1 and
    strands R2.
  - Matching finds the path `R2 → C1 → R1 → C2` and serves both. Show it in the Lab.
  - Greedy is still at least half of the optimum, because any maximal matching is.
- *Did matching beat greedy in the benchmarks?*
  - Only by one request, at density 2. Random uniform graphs rarely contain the trap.
  - Be honest about this: the guarantee matters under structured contention, not random demand.
- *How would you make it faster?*
  - Stop once the matching size reaches `min(L, R)`; this is future work, not implemented.
  - Or use Hopcroft-Karp, `O(E·√V)`.
- *Does it minimise waiting time?* No. It maximises the number of assignments, and the UI says so.

## Student 1 — Randomized Quickselect (CO5)

**In one sentence.** Finds the k-th smallest value in expected linear time by partitioning around a random pivot and
recursing into one side only.

**Method walkthrough.** `RandomizedQuickselect.select(values, k)`:

- The loop is iterative.
- The pivot index is `lo + random.nextInt(hi − lo + 1)`.
- The three-way partition produces `< pivot | = pivot | > pivot`, with the equal band at `[lt, gt]`.
- Narrow to the side that contains `k`, or return the pivot if `k` is in the band.
- The result carries the comparisons, swaps and partition rounds.

**Where it is used.** p50, p75 and p90 of waiting time and requested power: on the overview, in analytics, in each
allocation run's metrics and in simulations.

- The percentile index is nearest rank, `ceil(p·n) − 1`, computed in exact decimal arithmetic.
- **Why exact?** In IEEE-754 doubles `0.07 × 100 = 7.000000000000001`, so `ceil` gives 8 instead of 7 and the index `ceil(p·n) − 1` becomes 7 instead of 6. A test
  pins this.

**Correctness.**

- After the partition, the band holds exactly the elements whose sorted positions are `[lt, gt]`. So the answer is
  either the pivot or lies wholly in one side.
- Each round removes at least the pivot from the range, so the loop terminates.
- **Evidence.** 50 seeds × random arrays × many `k` checked against a sorted oracle.

**Randomness, the CO5 part.**

- The generator is **injected** (`RandomGenerator`), and tests and benchmarks pass `SplittableRandom(seed)`.
- The same seed gives the same pivots and the same counts (`sameSeedSameOperationCounts`).
- Different seeds take different paths to the same answer (`differentSeedsChooseDifferentPivots`).
- `pivotIsDrawnFromInjectedGenerator` shows the pivot is uniform over the active range, not fixed at the middle.

**Complexity.**

- **Expected `O(n)`.** A pivot in the middle half is "good" with probability ½, and each good pivot shrinks the
  range to at most ¾. Expected comparisons are therefore at most a geometric series in `n`.
- **Worst case `O(n²)`.** It needs every pivot to be extreme, which has exponentially small probability for any
  input.
- `O(1)` extra space.
- **The constant.** The classic bound for the median is about `2(1 + ln 2)·n ≈ 3.39n` comparisons in expectation.
- **Measured.** 2.2 to 4.4 comparisons per element across sizes and seeds, scattered around that expectation, and
  exactly `n` on all-equal input.

**Likely questions.**

- *Why three-way partitioning?* With a two-way Lomuto partition, all-equal data degrades to `n²/2`. Three-way puts
  everything in the band in one pass.
- *Why did the sort win on sorted input?*
  - The JDK's `Arrays.sort` detects existing runs, so presorted data costs it about one pass.
  - Random pivots cannot exploit existing order, which is the price of being immune to adversarial order.
  - Waiting-time data is unordered, so this does not affect ChargeWise.
- *Why not median-of-medians?* It gives a deterministic `O(n)` but with a large constant. The syllabus CO5 asks for
  randomisation, and the expected bound holds on every input.

## Student 2 — Edmonds-Karp maximum flow

**In one sentence.** Ford-Fulkerson with **BFS** to find each augmenting path: push the bottleneck along the
shortest residual path until the sink becomes unreachable.

**Method walkthrough.** `EdmondsKarp.maxFlow(network, source, sink)`:

- Each edge and its reverse arc are stored at ids `e` and `e ^ 1`, so updating the reverse arc is O(1).
- BFS records parent arcs.
- The bottleneck is the minimum residual on the path.
- Push the bottleneck forward and subtract it on the reverse arcs.
- When BFS fails, the visited set is the **source side of a minimum cut**.

**Where it is used.** Allocation step 8, and re-allocation after a grid-limit cut. The network is
`source → station (available W) → session (connector max) → sink (min(requested, vehicle max))`.
`MaxFlowPowerAllocator` maps each cut edge back to a named limit: `STATION_GRID`, `CONNECTOR`, `SESSION_DEMAND` or
`FEEDER`. Those are the bottlenecks shown in the UI.

**Correctness.**

- **Feasibility.** An augmentation never exceeds a residual, so `0 ≤ flow ≤ capacity` and conservation hold.
- **Optimality, by max-flow min-cut.** At termination every arc leaving the visited set `S` is saturated (otherwise
  BFS would cross it), and every arc entering `S` carries zero flow (otherwise its reverse arc would have residual).
  So the flow equals the cut's capacity, and no flow can exceed any cut.
- **Evidence.**
  - 300 random networks are checked against a brute-force minimum cut, and the CLRS 26.1 network gives 23.
  - `reverseResidualEdgeReroutesFlow` fails if reverse-arc updates are disabled.
  - `CapacityVerifier` independently re-checks every allocation. A violation rolls back the transaction.

**Complexity.**

- `O(V·E²)`. BFS gives shortest paths, and path lengths never decrease. Each edge can be the bottleneck `O(V)`
  times, so there are `O(V·E)` augmentations, each found by one `O(E)` BFS.
- `O(V + E)` space.
- **Measured.** 39 ms for 3,000 sessions at 10 stations, against 23.9 µs for greedy.

**Likely questions.**

- *Greedy got the same total. Why use max-flow?*
  - On the spec's network every session hangs off one station, so the network is a tree. On a tree, a
    work-conserving greedy is provably optimal. We proved this, tested it on 200 problems, and measured identical
    totals in all six benchmark cases.
  - Edmonds-Karp adds the **min-cut certificate**, the **bottleneck diagnosis**, and correctness when the model
    gains routing choice.
- *Show a case where greedy loses.*
  - Shared feeders: greedy feeds station A from F0 and strands B, for 100 kW. Edmonds-Karp re-routes A through F1,
    for 200 kW. Show it in the Lab.
  - Across 4 seeds × 6 generated cases, EK was ahead in 9 cases, by up to 7.17%, and never behind.
- *Why BFS and not DFS?* DFS-based Ford-Fulkerson has no polynomial bound on irrational or large capacities. BFS's
  shortest paths give `O(V·E)` augmentations.
- *Does it guarantee fairness?* No. It maximises total power only. Two equal sessions may get different shares.

## Student 2 — Segment tree

**In one sentence.** A binary tree over the 15-minute slot array. Each node stores the sum and the max of its range,
so a range query or point update touches `O(log n)` nodes.

**Method walkthrough.**

- `rangeMax(l, r)` / `rangeSum(l, r)` → recursive `query`: a node fully inside `[l, r]` returns its value; a range
  that falls entirely in one child recurses into that child only; otherwise it combines both children.
- `set` and `add` descend to the leaf and re-pull its ancestors.
- Sums use `Math.addExact`, so overflow throws instead of wrapping.

**Where it is used.**

- **Allocation step 7.** `available = grid − rangeMax(committed load over the horizon)`.
- **Analytics.** Utilisation is `rangeSum / (connectors × slots)`, and peak occupancy is `rangeMax`.

**Correctness.**

- By induction on node height, each node holds the aggregate of its range. A query decomposes `[l, r]` into
  disjoint canonical nodes, and sum and max are associative.
- **Safety of step 7.** For every slot, `committed(t) + new(t) ≤ max committed + Σ new ≤ grid`.
- **Evidence.** `queryAfterMultipleUpdates` checks 10,000 seeded operations against the naive scanner.

**Complexity.**

- Build `O(n)`, query and update `O(log n)`, space `O(n)` (4n entries).
- **Measured (JMH).** 39, 59 and 90 ns per operation at n = 1,000, 10,000 and 100,000, against 83 ns, 822 ns and
  8.0 µs for the naive scan.

**Likely questions.**

- *Why not a Fenwick tree?* A Fenwick tree gives range sums from prefix sums, but it cannot answer range max when
  values can go down as well as up, as they do when a booking is cancelled. We need both answers.
- *Why the peak over the horizon, not per slot?* It keeps one capacity per station for a single max-flow, and it is
  provably safe. The cost is some unused capacity in quiet slots, which is documented as a limitation.

---

## Shared: the allocation pipeline (both students)

Allocation runs in one transaction, under an advisory lock:

1. Load the pending and waitlisted requests, plus any whose window has already started. Station, connector and
   cancellation changes wait for the same lock.
2. Expire windows that have already started.
3. Quickselect percentiles.
4. Build the compatibility graph, recording a reason for every request with no edges.
5. Matching.
6. Segment-tree capacity per station.
7. Edmonds-Karp power.
8. `CapacityVerifier`.
9. Persist everything, with the full evidence JSON.

Any exception rolls back the whole run. A PostgreSQL exclusion constraint independently blocks overlapping
connector reservations.

**Questions either student may get.**

- *What happens when a station goes offline?* Future assignments become *Waitlisted* (reason *Station outage*) and
  in-progress sessions become *Interrupted*. The next run can re-match them elsewhere.
- *How is a simulation reproducible?* Every input derives from the saved seed. **Re-run and verify** recomputes the
  input hash and every non-timing metric; times differ, results do not.
- *Where did the AI tools help, and how do you know the code is right?* Answer honestly from your own experience.
  Then point at the evidence:
  - independent verifiers
  - brute-force oracle tests
  - mutation-checked tests that fail when the key line is broken
  - a benchmark report that keeps the results where our algorithm loses
