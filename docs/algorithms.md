# Algorithms

All four assessed algorithms are implemented from scratch in Java under
`backend/src/main/java/com/chargewise/algorithm/`. They depend only on the JDK.
`AlgorithmPurityTest` fails the build if any of them imports Spring, Jakarta or Jackson. Each
one has a baseline that receives identical input, and an independent verifier that checks
outputs before they are trusted or timed.

| Algorithm | Class | Baseline | Verifier | Unit |
|---|---|---|---|---|
| DFS augmenting-path bipartite matching | `matching.BipartiteMatcher` | `matching.GreedyFirstAvailableMatcher` | `matching.MatchingVerifier` | 5 |
| Edmonds-Karp maximum flow | `flow.EdmondsKarp`, applied by `power.MaxFlowPowerAllocator` | `power.GreedyPowerAllocator` | `power.CapacityVerifier` | 5 |
| Randomized Quickselect | `selection.RandomizedQuickselect`, `selection.Percentiles` | `selection.SortingSelector` | sorted-oracle tests | 6 (CO5) |
| Segment tree (sum + max) | `tree.ChargingAvailabilitySegmentTree` | `tree.NaiveRangeScanner` | naive-scan oracle tests | 3 |

Time and space bounds are derived in [complexity-analysis.md](complexity-analysis.md).

> **Objective statement** (verbatim from [architecture.md §6](architecture.md)): Matching
> maximises the *number* of request–connector assignments in the modeled compatibility graph.
> Edmonds-Karp maximises the *total feasible power* in the modeled network. Neither minimises
> waiting time or guarantees fairness.

To print a worked example of each algorithm, run:

```bash
cd backend
./mvnw -q compile exec:java -Dexec.mainClass=com.chargewise.algorithm.AlgorithmSampleRunner
```

---

## 1. Bipartite matching (DFS augmenting paths)

**Problem modelled.** Assign charging requests to connectors for one allocation run.

- The left vertices `U` are eligible requests and the right vertices `V` are connectors.
- The edge `(u, v)` exists only when connector `v` passes every compatibility rule for request
  `u`: connector type, operational status, no overlapping booking, and enough power to deliver
  the requested energy in the window. The rules are listed in
  [architecture.md §6](architecture.md).
- A matching gives each request at most one connector and each connector at most one request.

**Objective.** The algorithm maximises the *number* of request–connector assignments in this
graph. It does not minimise waiting time and does not guarantee fairness.

**Input.** A `BipartiteGraph`, built with `BipartiteGraph.builder(leftCount, rightCount).addEdge(u, v)`.

- Adjacency lists keep insertion order, so results are deterministic.
- Duplicate edges are ignored.
- Out-of-range indices and negative sizes throw `IllegalArgumentException`.

**Output.** A `MatchingResult` with these fields:

| Field | Meaning |
|---|---|
| `cardinality` | number of matched pairs |
| `matchOfLeft`, `matchOfRight` | the assignment in both directions (`-1` = unmatched) |
| `edgesExamined` | adjacency entries inspected |
| `augmentations` | augmenting paths found |
| `reassignments` | augmentations that moved an already-matched request |

**Pseudocode** (Kuhn's algorithm):

```text
matchL[u] = -1 for all u;  matchR[v] = -1 for all v
for u in 0 .. |U|-1:
    mark every right vertex unvisited          (a fresh "stamp")
    if tryAugment(u): cardinality += 1

tryAugment(u):                                  (DFS; implemented with an explicit stack)
    for v in adj(u) in insertion order:
        if v visited: continue
        mark v visited
        if matchR[v] == -1 or tryAugment(matchR[v]):
            matchL[u] = v;  matchR[v] = u       (flip the edge onto the path)
            return true
    return false
```

The implementation replaces recursion with an explicit stack of
`(left vertex, next neighbour index, right vertex descended through)` frames. When it finds a free
connector, it flips the whole path in one loop: request `stackLeft[k]` takes connector
`viaRight[k]`, and that connector's previous owner is `stackLeft[k+1]`, which takes the next
connector along. An augmenting path can be thousands of edges long; the test
`longAugmentingPathDoesNotOverflowStack` runs a 10,000-vertex chain to show that this cannot
overflow the thread stack.

**Correctness.**

1. *Validity invariant.* Every flip exchanges matched and unmatched edges along an alternating
   path that starts at an unmatched request and ends at a free connector. After the flip each
   vertex on the path is matched exactly once, so `matchL` and `matchR` stay mutually inverse
   and every matched pair is an edge. `MatchingVerifier` checks this independently.
2. *Maximality.* Berge's theorem: a matching is maximum iff no augmenting path exists. Kuhn's
   single pass is enough. If the search from `u` fails, no augmenting path from `u` exists in
   the current matching. Later augmentations only move requests along alternating paths, and
   cannot create one for `u`.
3. *Visited marks.* Within one search a connector already explored cannot lead to a new path,
   so marking it keeps each search to O(E) without losing paths.

**Baseline.** Greedy first-available visits requests in index order. Each request takes its
first free connector and the decision is never revisited.

- Greedy's matching is *maximal* (no edge can be added) but not necessarily *maximum*.
- **Constructed counterexample** (`augmentingPathReassignmentBeatsGreedy`): request 0 fits
  connectors {0, 1} and request 1 fits only {0}. Greedy gives connector 0 to request 0 and
  strands request 1, so it assigns 1 request. The augmenting path
  `request 1 → connector 0 → request 0 → connector 1` reassigns request 0, and both requests are
  served.
- Greedy is never worse than half the maximum, because any maximal matching is at least half a
  maximum one.

**Failure cases and handling.**

| Case | Handling |
|---|---|
| Empty graph | Returns an empty matching |
| A request with no edges | Stays unmatched. The allocation service records the first compatibility rule that removed every connector as the reason |
| More requests than connectors | Cardinality is at most the number of connectors; the rest are reported `LOST_CONTENTION` |
| Invalid indices | `IllegalArgumentException` |

**Tests:** `BipartiteMatcherTest` (10), `BipartiteGraphTest` (6), `GreedyFirstAvailableMatcherTest` (4).

- These include the spec §12 cases: single match, many matches, no edges, more requests than
  connectors, contention, an empty graph and the greedy counterexample.
- `randomGraphsMatchBruteForceMaximum` compares 200 seeded random graphs against exhaustive
  search.

---

## 2. Edmonds-Karp maximum flow (power allocation)

**Problem modelled.** Decide how many watts each matched charging session receives for the
allocation interval, under the constant-power model. The flow network has these layers
(spec §6.2):

```text
source --(station grid limit)--> station i --(connector max)--> session j --(min(requested, vehicle max))--> sink
```

- The `source → station` edge caps the *total* drawn by all sessions at a station.
- The `station → session` edge caps each session at its connector's rating.
- The `session → sink` edge caps it at the vehicle's demand.

**Extension D12: shared feeders.** Stations can hang off shared distribution feeders:

```text
source --feeder cap--> feeder f --station limit--> stationIn i --station limit--> station i -> ...
```

The single `stationIn → station` gate keeps a dual-fed station within its grid limit. This layer
gives the network genuine routing choices. See [architecture.md §16](architecture.md).

**Objective.** The maximum flow is the largest *total* power that can be delivered without
exceeding any feeder, station, connector or vehicle limit. It does **not** minimise waiting
time, maximise the number of fully charged vehicles, or guarantee a fair split between sessions.

**Units.** Power is an integer number of watts (`long`). `PowerUnits.kwToWatts` converts
2-decimal kW exactly and rejects anything that would need rounding. Integer capacities make
every augmentation integral, so the result is exact.

**Pseudocode:**

```text
flow(e) = 0 for every edge
loop:
    BFS from source over arcs with residual(e) = capacity(e) - flow(e) > 0, recording parent arcs
    if sink not reached:
        the visited set S is the source side of a minimum cut; stop
    b = min residual(e) over the parent path            (bottleneck)
    for each arc e on the path:
        flow(e) += b;  flow(reverse(e)) -= b            (reverse arcs allow later rerouting)
    total += b
```

`FlowNetwork` stores each edge and its reverse arc at ids `e` and `e ^ 1`, so updating the
reverse arc is O(1). Adjacency is iterated in insertion order, which makes path choice
deterministic.

**Correctness.**

1. *Feasibility invariant.* An augmentation never pushes more than the minimum residual on the
   path. Every arc therefore keeps `0 ≤ flow ≤ capacity`. Each internal node on the path gains
   exactly as much inflow as outflow, so conservation holds.
2. *Optimality (max-flow min-cut).* When BFS cannot reach the sink, the visited set `S` defines
   a cut. Every arc leaving `S` is saturated, otherwise BFS would have crossed it. Every arc
   entering `S` carries zero flow, otherwise its reverse arc would have positive residual. The
   flow value therefore equals that cut's capacity. Since no flow can exceed any cut, the flow
   is maximum and the cut is minimum.
3. *Rerouting.* Reverse residual arcs let a later path cancel flow that an earlier path chose
   badly. In `reverseResidualEdgeReroutesFlow`, the first shortest path is `s-a-x-t`; the second
   path `s-b-x-a-y-t` cancels the flow on `a→x` through the reverse arc `x→a`. A mutation test
   that disables reverse-arc updates makes this test fail.

**Bottleneck reporting.** `MaxFlowPowerAllocator` maps each min-cut edge back to the constraint
it represents: `FEEDER`, `STATION_GRID`, `CONNECTOR` or `SESSION_DEMAND`. These are the limits
that would have to rise to deliver more power. A `SESSION_DEMAND` entry means that session is
fully served.

**Baseline.** The greedy priority allocator visits sessions in index order. Each session gets
`min(connector, demand, station headroom)`; with feeders, it is also capped by its feeders'
remaining capacity, drawn from those feeders in link order. Two results follow, both pinned by
tests:

- **Direct (spec) model.** Every session hangs off exactly one station, so the network is a
  tree. Its maximum total is `Σ_station min(capacity, Σ_sessions min(connector, demand))`, and
  work-conserving greedy reaches it too. EK and greedy therefore deliver the **same total**:
  `equalsGreedyTotalOnTreeShapedModel` checks 200 seeded problems. On this model, Edmonds-Karp's
  contributions are the min-cut certificate, the bottleneck diagnosis, and correctness under
  model generalisation. It does not deliver more power here.
- **Feeder model.** Greedy can strand power. In `feederRoutingBeatsFixedOrderGreedy`, station A
  is fed by F0 then F1, and station B only by F0. Greedy serves A from F0 and leaves B with
  nothing (100 kW total). Edmonds-Karp routes A through F1 (200 kW).
  `randomFeederProblemsEkNeverBelowGreedy` checks EK ≥ greedy on 200 seeded problems. Any gain on
  generated scenarios will be reported only as measured.

**Independent verification.** `CapacityVerifier` checks every result against the problem, with
no knowledge of how it was produced. It checks:

- non-negative grants;
- each session's connector and demand limits;
- each station's grid limit;
- feeder ratings, and that link flows into each station equal the station's allocation;
- that reported totals are internally consistent.

The allocation pipeline aborts the transaction if any violation is found.

**Failure cases and handling.**

| Case | Handling |
|---|---|
| No sessions, no path, zero capacities | Total 0; zero-capacity limits appear as bottlenecks |
| Negative capacity, self-loop, unknown node or station, mismatched arrays, duplicate feeder link | `IllegalArgumentException` |
| Network that already carries flow | `IllegalStateException` |
| Sums beyond `long` | `ArithmeticException` (never silent wrap-around) |

**Tests:**

| Test class | Tests | Notes |
|---|---|---|
| `EdmondsKarpTest` | 11 | Spec cases plus the CLRS 26.1 network (23); 300 random networks checked against a brute-force minimum cut |
| `FlowNetworkTest` | 4 | |
| `MaxFlowPowerAllocatorTest` | 12 | |
| `GreedyPowerAllocatorTest` | 3 | |
| `CapacityVerifierTest` | 7 | |
| `PowerUnitsTest` | 5 | |

---

## 3. Randomized Quickselect (CO5)

**Problem modelled.** Waiting-time and demand percentiles (p50, p75, p90) for operator analytics,
without sorting every observation.

**Percentile convention (nearest rank).** The p-th percentile of `n` values is the order
statistic at zero-based index `k = ceil(p·n) − 1`, for `0 < p ≤ 1`.

`Percentiles.nearestRankIndex` evaluates this formula in exact decimal arithmetic. In IEEE-754
doubles, `0.07 × 100 = 7.000000000000001`, so a naive `Math.ceil(p * n) − 1` returns rank 7
instead of 6. The same happens for `0.55 × 100` and `0.14 × 50`; these values are pinned by
`nearestRankIndexIsExactForDecimalPercentiles`, and a mutation test confirms the naive formula
fails it. The sorting baseline uses the same index, so both methods answer the same question.

**Pseudocode:**

```text
select(A, k):                                  (iterative, in place)
    lo = 0; hi = n - 1
    while lo < hi:
        pivot = A[lo + random.nextInt(hi - lo + 1)]          (uniform over the active range)
        three-way partition A[lo..hi] into  < pivot | == pivot | > pivot
            with boundaries lt (first ==) and gt (last ==)
        if k < lt:  hi = lt - 1
        elif k > gt: lo = gt + 1
        else: return pivot
    return A[lo]
```

**Randomness and reproducibility.** The pivot comes from an injected
`java.util.random.RandomGenerator`.

- Tests, benchmarks and simulations pass `new SplittableRandom(seed)` and record the seed, so a
  run can be reproduced exactly (`sameSeedSameOperationCounts`).
- `pivotIsDrawnFromInjectedGenerator` records every `nextInt(bound)` call. It shows that the
  pivot index is drawn over the active range (first bound = n), not fixed at the middle.
- `differentSeedsChooseDifferentPivots` shows that different seeds take different partition
  sequences to the same answer.

**Correctness.**

- After partitioning, every element left of `lt` is smaller than the pivot, every element right
  of `gt` is larger, and the band `[lt, gt]` holds only pivot-equal values. The sorted position
  of every element in the band is therefore exactly its current index range. If `k` lies in the
  band, the answer is the pivot; otherwise the answer lies entirely in the side that contains
  `k`.
- Each round strictly shrinks the range, because the band always contains the pivot, so the
  loop terminates.
- **Three-way partitioning** is what keeps duplicate-heavy and all-equal inputs linear. On
  all-equal data the first round places everything in the band
  (`allValuesEqual`: ≤ 2n comparisons). A two-way Lomuto partition would degrade to n²/2.

**Baseline.** `SortingSelector` sorts a copy (`Arrays.sort`, O(n log n)) and reads index `k`. It
reports `comparisons = −1` because the library sort exposes no counter; estimating one would be
fabrication.

**Failure cases.** Null or empty input, `k` outside `[0, n)`, `p` outside `(0, 1]` (including
NaN) and `n < 1` all throw `IllegalArgumentException`. `Long.MIN_VALUE` and `Long.MAX_VALUE` are
handled (`multipleSeedsAgreeWithSortedOracle`).

**Tests:**

| Test class | Tests | Notes |
|---|---|---|
| `RandomizedQuickselectTest` | 10 | Includes 50 seeds × random arrays × many `k`, checked against the sorted oracle |
| `PercentilesTest` | 4 | |
| `SortingSelectorTest` | 2 | |

Quickselect supports analytics only; it does not drive matching or power allocation.

---

## 4. Segment tree (range sum and range max)

**Problem modelled.** Per-station arrays indexed by 15-minute slot. The tree serves two uses:

- **Committed load (watts).** The allocation pipeline subtracts the peak, `rangeMax`, over a
  batch horizon from the station's grid limit (architecture D4 step 7).
- **Occupied connectors.** `rangeSum` / (connectors × slots) gives utilization, and `rangeMax`
  gives peak occupancy.

**Conventions.**

- Indices are zero-based and ranges are inclusive `[l, r]`.
- `l > r`, `l < 0`, `r ≥ size` and any query on an empty tree throw `IllegalArgumentException`.
- `set(i, v)` replaces a value; `add(i, d)` adjusts it.
- Sums use `Math.addExact`. A build or update that would overflow throws `ArithmeticException`
  instead of wrapping.

**Structure.** Node 1 covers `[0, n−1]`. Node `v` covering `[lo, hi]` has children `2v`
(`[lo, mid]`) and `2v+1` (`[mid+1, hi]`), with `mid = (lo + hi) >>> 1`. Each node stores both the
sum and the max of its range.

```text
build(v, lo, hi):    leaf -> (A[lo], A[lo]); else build children, pull(v)
update(v, lo, hi, i, x): descend to leaf i, set it, pull every ancestor
query(v, lo, hi, l, r):
    [lo, hi] inside [l, r]          -> return node value
    [l, r] entirely in one child    -> recurse into that child only
    otherwise                       -> combine(query(left), query(right))
pull(v): sum = sum(2v) + sum(2v+1);  max = max(max(2v), max(2v+1))
```

**Correctness.** By induction on node height, each node's `(sum, max)` equals the aggregate of
its range:

- leaves hold their element;
- `pull` combines two disjoint child ranges whose union is the parent's range;
- an update changes one leaf and re-pulls exactly the ancestors whose ranges contain it.

A query decomposes `[l, r]` into disjoint canonical node ranges whose union is `[l, r]`.
Sum and max are associative over disjoint unions, so the combined answer is exact.

**Baseline.** `NaiveRangeScanner` makes O(1) updates and scans each range in O(r − l + 1).

**Tests:** `ChargingAvailabilitySegmentTreeTest` (11).

- These include the spec cases plus non-power-of-two sizes, all-negative max, and defensive
  copying.
- `queryAfterMultipleUpdates` runs 10,000 seeded `set` / `add` / query operations and checks
  that the tree agrees with the naive scanner.
