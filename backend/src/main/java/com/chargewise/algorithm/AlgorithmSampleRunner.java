package com.chargewise.algorithm;

import com.chargewise.algorithm.flow.EdmondsKarp;
import com.chargewise.algorithm.flow.FlowNetwork;
import com.chargewise.algorithm.flow.MaxFlowResult;
import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.algorithm.matching.BipartiteMatcher;
import com.chargewise.algorithm.matching.GreedyFirstAvailableMatcher;
import com.chargewise.algorithm.matching.MatchingResult;
import com.chargewise.algorithm.power.FeederTopology;
import com.chargewise.algorithm.power.GreedyPowerAllocator;
import com.chargewise.algorithm.power.MaxFlowPowerAllocator;
import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.power.PowerUnits;
import com.chargewise.algorithm.selection.Percentiles;
import com.chargewise.algorithm.selection.SortingSelector;
import com.chargewise.algorithm.tree.ChargingAvailabilitySegmentTree;
import java.io.PrintStream;
import java.util.SplittableRandom;

/**
 * Prints small worked examples computed by each algorithm, for demonstrations and viva walkthroughs.
 *
 * <p>Run: {@code ./mvnw -q compile exec:java -Dexec.mainClass=com.chargewise.algorithm.AlgorithmSampleRunner}
 */
public final class AlgorithmSampleRunner {

    private static final long PERCENTILE_SEED = 20261102L;
    private static final long KW = 1_000L;

    private AlgorithmSampleRunner() {
    }

    public static void main(String[] args) {
        run(System.out);
    }

    public static void run(PrintStream out) {
        matchingExample(out);
        flowExample(out);
        feederExample(out);
        percentileExample(out);
        segmentTreeExample(out);
    }

    private static void matchingExample(PrintStream out) {
        // Request 0 fits connectors 0 and 1; request 1 fits only connector 0.
        BipartiteGraph graph = BipartiteGraph.builder(2, 2).addEdge(0, 0).addEdge(0, 1).addEdge(1, 0).build();
        MatchingResult greedy = new GreedyFirstAvailableMatcher().match(graph);
        MatchingResult augmenting = new BipartiteMatcher().match(graph);

        out.println("== Bipartite matching ==");
        out.printf("Greedy first-available matching: %d of %d requests assigned%n",
                greedy.cardinality(), graph.leftCount());
        out.printf("Augmenting-path matching: %d of %d requests assigned (%d reassignment%s)%n",
                augmenting.cardinality(), graph.leftCount(), augmenting.reassignments(),
                augmenting.reassignments() == 1 ? "" : "s");
    }

    private static void flowExample(PrintStream out) {
        int s = 0, a = 1, b = 2, x = 3, y = 4, t = 5;
        FlowNetwork network = new FlowNetwork(6);
        network.addEdge(s, a, 1);
        network.addEdge(s, b, 1);
        int aToX = network.addEdge(a, x, 1);
        network.addEdge(a, y, 1);
        network.addEdge(b, x, 1);
        network.addEdge(x, t, 1);
        network.addEdge(y, t, 1);
        MaxFlowResult result = new EdmondsKarp().maxFlow(network, s, t);

        out.println("== Edmonds-Karp ==");
        out.printf("Edmonds-Karp total flow: %d (flow on a->x after rerouting: %d)%n",
                result.totalFlow(), network.flow(aToX));
    }

    private static void feederExample(PrintStream out) {
        FeederTopology feeders = new FeederTopology(new long[] {100 * KW, 100 * KW}, new int[] {0, 1, 0}, new int[] {0, 0, 1});
        PowerAllocationProblem problem = PowerAllocationProblem.withFeeders(
                new long[] {100 * KW, 100 * KW}, new int[] {0, 1}, new long[] {150 * KW, 150 * KW},
                new long[] {100 * KW, 100 * KW}, new long[] {150 * KW, 150 * KW}, feeders);
        long flow = new MaxFlowPowerAllocator().allocate(problem).totalAllocatedW();
        long greedy = new GreedyPowerAllocator().allocate(problem).totalAllocatedW();

        out.printf("Feeder example: Edmonds-Karp %s kW, greedy %s kW%n",
                PowerUnits.wattsToKw(flow).toPlainString(), PowerUnits.wattsToKw(greedy).toPlainString());
    }

    private static void percentileExample(PrintStream out) {
        long[] waitingMinutes = new SplittableRandom(PERCENTILE_SEED).longs(1_000, 0, 120).toArray();
        double[] ps = {0.5, 0.75, 0.9};
        long[] quickselect = Percentiles.compute(waitingMinutes, ps, new SplittableRandom(PERCENTILE_SEED));
        SortingSelector sorting = new SortingSelector();
        boolean agrees = true;
        for (int i = 0; i < ps.length; i++) {
            int k = Percentiles.nearestRankIndex(ps[i], waitingMinutes.length);
            agrees &= sorting.select(waitingMinutes, k).value() == quickselect[i];
        }

        out.println("== Randomized Quickselect ==");
        out.printf("Waiting-time percentiles over %d synthetic values (seed %d): p50=%d min, p75=%d min, p90=%d min;"
                + " matches full sort: %b%n", waitingMinutes.length, PERCENTILE_SEED,
                quickselect[0], quickselect[1], quickselect[2], agrees);
    }

    private static void segmentTreeExample(PrintStream out) {
        ChargingAvailabilitySegmentTree tree = new ChargingAvailabilitySegmentTree(new long[] {3, 1, 4, 1, 5, 9, 2, 6});

        out.println("== Segment tree ==");
        out.printf("Segment tree rangeSum[2,5] = %d, rangeMax[2,5] = %d%n", tree.rangeSum(2, 5), tree.rangeMax(2, 5));
    }
}
