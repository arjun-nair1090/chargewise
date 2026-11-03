package com.chargewise.benchmark;

/** Each assessed algorithm against its baseline (project brief §10). */
public enum BenchmarkFamily {
    /** Bipartite matching against greedy first-available. */
    MATCHING,
    /** Edmonds-Karp against the greedy allocator on the spec §6.2 network. */
    POWER,
    /** Edmonds-Karp against the greedy allocator with the D12 shared feeders. */
    POWER_FEEDERS,
    /** Randomized Quickselect against a full sort. */
    SELECTION,
    /** Segment tree range queries and updates against a naive scan. */
    RANGE_QUERY
}
