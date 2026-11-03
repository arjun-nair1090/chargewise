package com.chargewise.algorithm.matching;

/**
 * Outcome of a matching run. {@code matchOfLeft[u]} is the right vertex assigned to left vertex
 * {@code u}, or {@code -1} when unmatched; {@code matchOfRight} is the inverse map.
 *
 * @param edgesExamined adjacency entries inspected (the unit of work for the O(VE) bound)
 * @param augmentations successful augmenting paths found (equals {@code cardinality})
 * @param reassignments augmentations that moved at least one previously matched left vertex
 */
public record MatchingResult(
        int cardinality,
        int[] matchOfLeft,
        int[] matchOfRight,
        long edgesExamined,
        int augmentations,
        int reassignments) {

    public MatchingResult {
        matchOfLeft = matchOfLeft.clone();
        matchOfRight = matchOfRight.clone();
    }

    @Override
    public int[] matchOfLeft() {
        return matchOfLeft.clone();
    }

    @Override
    public int[] matchOfRight() {
        return matchOfRight.clone();
    }
}
