package com.chargewise.algorithm.flow;

import java.util.List;

/**
 * Outcome of a maximum-flow computation. Per-edge flows remain readable on the network itself.
 *
 * @param sourceSideOfMinCut nodes reachable from the source in the final residual graph
 * @param minCutEdges forward edge ids leaving that set: the saturated bottleneck whose total
 *     capacity equals {@code totalFlow} (max-flow min-cut theorem)
 */
public record MaxFlowResult(
        long totalFlow,
        int augmentations,
        int bfsRuns,
        long edgesScanned,
        boolean[] sourceSideOfMinCut,
        List<Integer> minCutEdges) {

    public MaxFlowResult {
        sourceSideOfMinCut = sourceSideOfMinCut.clone();
        minCutEdges = List.copyOf(minCutEdges);
    }

    @Override
    public boolean[] sourceSideOfMinCut() {
        return sourceSideOfMinCut.clone();
    }
}
