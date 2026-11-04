# Complexity Analysis

Notation:

- matching graph: `L` requests, `R` connectors, `E` edges, `V = L + R`;
- flow networks: `V` nodes, `E` edges;
- power problems: `S` stations, `J` sessions, `F` feeders, `L` feeder links;
- selection and segment trees: `n` elements.

These are theoretical bounds. Measured runtimes appear only in
[benchmark-report.md](benchmark-report.md), after they have been measured.

## Summary

| Algorithm | Time | Auxiliary space | Baseline | Baseline time |
|---|---|---|---|---|
| Bipartite matching (Kuhn, DFS) | O(V·E), precisely O(L·(R + E)) | O(L + R) | Greedy first-available | O(L + E) |
| Edmonds-Karp | O(V·E²) | O(V + E) | — | — |
| Power allocation via EK | O(V·E²) on V = S + J + 2 (direct) or F + 2S + J + 2 (feeders) | O(V + E) | Greedy priority | O(S + J + L) |
| Randomized Quickselect | expected O(n), worst O(n²) | O(1) | Full sort | O(n log n) |
| Segment tree | build O(n); update and query O(log n) | O(n) | Naive scan | update O(1); query O(n) |

## 1. Bipartite matching — O(V·E)

- The outer loop runs one augmenting-path search per request, so `L` searches.
- Within one search, a fresh visit stamp ensures each connector is explored at most once.
  Each request's adjacency list is scanned at most once, because a request enters the DFS stack
  only through the connector it owns, and that connector is visited once.
- One search therefore costs O(R + E). The total is O(L·(R + E)) ⊆ O(V·E) for a connected
  graph.

This is the bound the `edgesExamined` counter measures.

*Space.* The arrays `matchOfLeft`, `matchOfRight` and `visitStamp`, plus an explicit DFS stack
of at most `L` frames (each frame holds a distinct request), give O(L + R). Stamping a fresh
integer avoids an O(R) clearing pass per search.

*Greedy baseline.* Each request scans its adjacency once, so O(L + E). It is fast but can return
as few as half the maximum assignments.

## 2. Edmonds-Karp — O(V·E²)

**Number of augmentations is O(V·E).**

1. *Distances never decrease.* Let `d(v)` be the BFS distance from the source in the residual
   graph. Augmenting along a shortest path only adds reverse arcs `(v, u)` with
   `d(v) = d(u) + 1`, which can never create a shortcut. So `d(v)` is non-decreasing over time.
2. *Each arc is critical O(V) times.* When an arc `(u, v)` is critical (it determines the
   bottleneck), it saturates and leaves the residual graph, with `d(v) = d(u) + 1`. It can
   reappear only after flow is pushed along `(v, u)`, which requires `d(u) = d(v) + 1`. So `d(u)`
   has grown by at least 2 between two critical uses. Since `d(u) ≤ V`, each arc is critical
   O(V) times.
3. *Every augmentation has a critical arc.* There are 2E residual arcs, so there are at most
   O(V·E) augmentations.

**Each augmentation costs O(E):** one BFS over the residual arcs, plus an O(V) walk along the
path.

**Total:** O(V·E) × O(E) = **O(V·E²)**. Space is O(V + E): the arc arrays and the BFS queue,
parent and visited arrays.

The `bfsRuns`, `augmentations` and `edgesScanned` counters in `MaxFlowResult` instrument exactly
these quantities.

**Power networks in practice.** The direct network has only three layers, so every
source-to-sink path has length 3 until reverse arcs come into play. Each augmentation saturates
at least one edge on the path. For the spec model the number of augmentations stays small
relative to the worst-case bound. The benchmark report will measure this rather than assume it.

**Greedy power baseline.** One pass over sessions plus each station's links gives
O(S + J + L).

## 3. Randomized Quickselect — expected O(n), worst O(n²)

Each partitioning round costs one comparison per element of the active range.

- **Expected linear time.** Call a pivot *good* if its rank lies in the middle half of the active
  range. A good pivot shrinks the range to at most ¾ of its size. A uniformly random pivot is
  good with probability ½, so the expected number of rounds between good pivots is at most 2.
  Summing over the phases where the range is in `((¾)^{j+1}·n, (¾)^j·n]` gives expected work
  ≤ `Σ_j 2·(¾)^j·n = 8n`, which is O(n).
- **The constant, more precisely.** The classic analysis of uniform-pivot Quickselect bounds the
  expected comparisons by `2n + 2k·ln(n/k) + 2(n−k)·ln(n/(n−k))`. For the median this is
  `2(1 + ln 2)·n ≈ 3.39n`. The test bound `comparisons < 10n` on 100,000 sorted and
  reverse-sorted elements is a conservative check against this. The input order does not
  matter, because the pivot is random.
- **Worst case O(n²).** If every pivot is the minimum or maximum of its range, each round removes
  only the pivot band, giving `n + (n−1) + … = O(n²)`. Randomization makes this unlikely for
  *every* input; the probability is exponentially small in `n`. It is not impossible.
- **Duplicates.** The three-way partition removes every pivot-equal element in the same round. An
  all-equal array finishes after a single partition pass, at most `2n` comparisons (pinned by
  `allValuesEqual`). A two-way partition that sends
  equal elements to one side would make each round remove one element, giving Θ(n²).
- **Space.** The loop is iterative and in place, so auxiliary space is O(1) beyond the input.
  `Percentiles.compute` copies the input once, O(n), so the caller's array is untouched.

**Sorting baseline.** O(n log n) time and O(n) for the copy. For a handful of percentiles,
Quickselect costs O(m·n) expected for `m` percentiles; sorting costs O(n log n) once. The
benchmarks compare them on uniform, sorted, reverse-sorted, duplicate-heavy and all-equal
inputs.

## 4. Segment tree — build O(n), update/query O(log n)

- **Build.** The tree has fewer than `2n` nodes, and each is computed once from its children in
  O(1), so build is O(n).
- **Point update.** The tree is height-balanced with height `⌈log₂ n⌉`. An update re-pulls one
  node per level, O(log n). `add(i, d)` first reads the leaf, a single-element query, in
  O(log n).
- **Range query.** At each level at most two nodes partially overlap `[l, r]`, namely those
  containing the endpoints. Every other visited node is either fully inside, so it returns
  immediately, or outside, so it is never entered because the query only descends into children
  that intersect the range. At most 4 nodes are visited per level, so a query is O(log n).
- **Space.** The `sum` and `max` arrays have `4n` entries each, which is enough for any `n`.
  Space is O(n).

**Naive baseline.** O(1) update and O(r − l + 1) = O(n) query.

The trade-off favours the tree when queries are frequent or ranges are long. In the allocation
pipeline, every run queries the peak committed load over a horizon of up to 48 slots for each
station.
