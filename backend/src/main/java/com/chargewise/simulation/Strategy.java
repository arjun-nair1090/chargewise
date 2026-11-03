package com.chargewise.simulation;

/** Which allocators a simulation run uses: the assessed algorithms, or the greedy baselines they are compared with. */
public enum Strategy {
    /** Bipartite matching (augmenting paths), then Edmonds-Karp power allocation. */
    ALGORITHM,
    /** Greedy first-available matching, then the greedy fixed-order power allocator. */
    BASELINE
}
