package com.chargewise.algorithm.matching;

/** A strategy that assigns left vertices to distinct right vertices along graph edges. */
public interface MatchingAlgorithm {

    String name();

    MatchingResult match(BipartiteGraph graph);
}
