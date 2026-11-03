package com.chargewise.service;

import com.chargewise.algorithm.flow.EdmondsKarp;
import com.chargewise.algorithm.flow.FlowNetwork;
import com.chargewise.algorithm.flow.MaxFlowResult;
import com.chargewise.algorithm.matching.BipartiteGraph;
import com.chargewise.algorithm.matching.BipartiteMatcher;
import com.chargewise.algorithm.matching.GreedyFirstAvailableMatcher;
import com.chargewise.algorithm.matching.MatchingAlgorithm;
import com.chargewise.algorithm.matching.MatchingResult;
import com.chargewise.algorithm.matching.MatchingVerifier;
import com.chargewise.algorithm.power.CapacityVerifier;
import com.chargewise.algorithm.power.FeederTopology;
import com.chargewise.algorithm.power.GreedyPowerAllocator;
import com.chargewise.algorithm.power.MaxFlowPowerAllocator;
import com.chargewise.algorithm.power.PowerAllocationProblem;
import com.chargewise.algorithm.power.PowerAllocationResult;
import com.chargewise.algorithm.power.PowerAllocator;
import com.chargewise.algorithm.power.PowerUnits;
import com.chargewise.algorithm.selection.Percentiles;
import com.chargewise.algorithm.selection.RandomizedQuickselect;
import com.chargewise.algorithm.selection.SelectionResult;
import com.chargewise.algorithm.selection.SortingSelector;
import com.chargewise.algorithm.tree.ChargingAvailabilitySegmentTree;
import com.chargewise.algorithm.tree.NaiveRangeScanner;
import com.chargewise.algorithm.tree.RangeAggregate;
import com.chargewise.dto.lab.MatchingLabRequest;
import com.chargewise.dto.lab.MatchingLabResponse;
import com.chargewise.dto.lab.MatchingLabResponse.MatchedPair;
import com.chargewise.dto.lab.MatchingLabResponse.MatchingRun;
import com.chargewise.dto.lab.MaxFlowLabRequest;
import com.chargewise.dto.lab.MaxFlowLabRequest.FeederInput;
import com.chargewise.dto.lab.MaxFlowLabRequest.FeederLinkInput;
import com.chargewise.dto.lab.MaxFlowLabRequest.FlowEdgeInput;
import com.chargewise.dto.lab.MaxFlowLabRequest.NetworkInput;
import com.chargewise.dto.lab.MaxFlowLabRequest.PowerInput;
import com.chargewise.dto.lab.MaxFlowLabRequest.SessionInput;
import com.chargewise.dto.lab.MaxFlowLabRequest.StationInput;
import com.chargewise.dto.lab.MaxFlowLabResponse;
import com.chargewise.dto.lab.MaxFlowLabResponse.EdgeFlow;
import com.chargewise.dto.lab.MaxFlowLabResponse.LabBottleneck;
import com.chargewise.dto.lab.MaxFlowLabResponse.NetworkRun;
import com.chargewise.dto.lab.MaxFlowLabResponse.PowerComparison;
import com.chargewise.dto.lab.MaxFlowLabResponse.PowerRun;
import com.chargewise.dto.lab.MaxFlowLabResponse.SessionPower;
import com.chargewise.dto.lab.MaxFlowLabResponse.StationPower;
import com.chargewise.dto.lab.QuickselectLabRequest;
import com.chargewise.dto.lab.QuickselectLabResponse;
import com.chargewise.dto.lab.QuickselectLabResponse.Selection;
import com.chargewise.dto.lab.QuickselectLabResponse.SelectionWork;
import com.chargewise.dto.lab.SegmentTreeLabRequest;
import com.chargewise.dto.lab.SegmentTreeLabRequest.OperationType;
import com.chargewise.dto.lab.SegmentTreeLabRequest.TreeOperation;
import com.chargewise.dto.lab.SegmentTreeLabResponse;
import com.chargewise.dto.lab.SegmentTreeLabResponse.LabTiming;
import com.chargewise.dto.lab.SegmentTreeLabResponse.QueryResult;
import com.chargewise.exception.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Algorithm Lab: each endpoint runs the core algorithm and its baseline on the same input and reports results,
 * operation counts and runtimes. Inputs are capped (architecture D7) so a request cannot exhaust the server.
 */
@Service
public class AlgorithmLabService {

    static final int MAX_VERTICES = 5_000;
    static final int MAX_EDGES = 50_000;
    static final int MAX_VALUES = 1_000_000;
    /** The naive baseline scans its range per query, so the work it may do is bounded separately. */
    static final int MAX_TREE_OPERATIONS = 1_000;
    static final long MAX_NAIVE_SCAN_WORK = 50_000_000L;
    /** Keeps every possible range sum (≤ 10^6 values, ≤ 10^3 additions) far inside {@code long}. */
    static final long MAX_ABS_TREE_VALUE = 1_000_000_000_000L;
    static final long DEFAULT_SEED = 42L;

    public MatchingLabResponse matching(MatchingLabRequest request) {
        capVertices(request.requests().size() + request.connectors().size());
        capEdges(request.edges().size());
        Map<String, Integer> left = index(request.requests(), "requests");
        Map<String, Integer> right = index(request.connectors(), "connectors");
        BipartiteGraph.Builder builder = BipartiteGraph.builder(left.size(), right.size());
        for (int i = 0; i < request.edges().size(); i++) {
            MatchingLabRequest.MatchingEdge edge = request.edges().get(i);
            Integer u = left.get(edge.request());
            Integer v = right.get(edge.connector());
            if (u == null || v == null) {
                throw ApiException.validation("edges", "edge " + i + " references an unknown "
                        + (u == null ? "request '" + edge.request() + "'" : "connector '" + edge.connector() + "'"));
            }
            builder.addEdge(u, v);
        }
        BipartiteGraph graph = builder.build();
        return new MatchingLabResponse(runMatching(new BipartiteMatcher(), graph, request),
                runMatching(new GreedyFirstAvailableMatcher(), graph, request));
    }

    public MaxFlowLabResponse maxFlow(MaxFlowLabRequest request) {
        if ((request.network() == null) == (request.power() == null)) {
            throw ApiException.validation("network", "provide exactly one of network or power");
        }
        return request.network() != null ? new MaxFlowLabResponse("NETWORK", network(request.network()), null)
                : new MaxFlowLabResponse("POWER", null, power(request.power()));
    }

    public QuickselectLabResponse quickselect(QuickselectLabRequest request) {
        int n = request.values().size();
        capValues(n);
        if (n == 0) {
            throw ApiException.validation("values", "must not be empty");
        }
        boolean hasPercentiles = request.percentiles() != null && !request.percentiles().isEmpty();
        if ((request.k() != null) == hasPercentiles) {
            throw ApiException.validation("k", "provide exactly one of k or percentiles");
        }
        if (request.k() != null && request.k() > n) {
            throw ApiException.validation("k", "must be at most the number of values (" + n + ")");
        }
        List<String> labels = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        if (request.k() != null) {
            labels.add("k=" + request.k());
            ranks.add(request.k() - 1);
        } else {
            for (double p : request.percentiles()) {
                labels.add("p" + (p == Math.rint(p) ? Long.toString((long) p) : Double.toString(p)));
                ranks.add(Percentiles.nearestRankIndexFromPercent(p, n));
            }
        }
        long seed = request.seed() != null ? request.seed() : DEFAULT_SEED;
        long[] values = request.values().stream().mapToLong(Long::longValue).toArray();

        RandomizedQuickselect quickselect = new RandomizedQuickselect(new SplittableRandom(seed));
        long[] working = values.clone();
        SelectionResult[] selected = new SelectionResult[ranks.size()];
        long start = System.nanoTime();
        for (int i = 0; i < ranks.size(); i++) {
            selected[i] = quickselect.select(working, ranks.get(i));
        }
        long quickselectNs = System.nanoTime() - start;

        SortingSelector sorter = new SortingSelector();
        SelectionResult[] sorted = new SelectionResult[ranks.size()];
        start = System.nanoTime();
        for (int i = 0; i < ranks.size(); i++) {
            sorted[i] = sorter.select(values, ranks.get(i));
        }
        long sortNs = System.nanoTime() - start;

        List<Selection> selections = new ArrayList<>();
        long comparisons = 0;
        long swaps = 0;
        int rounds = 0;
        boolean agree = true;
        for (int i = 0; i < ranks.size(); i++) {
            selections.add(new Selection(labels.get(i), ranks.get(i), selected[i].value(), sorted[i].value()));
            agree &= selected[i].value() == sorted[i].value();
            comparisons += selected[i].comparisons();
            swaps += selected[i].swaps();
            rounds += selected[i].partitionRounds();
        }
        return new QuickselectLabResponse(n, seed, agree, selections,
                new SelectionWork(RandomizedQuickselect.NAME, comparisons, swaps, rounds, quickselectNs),
                new SelectionWork(SortingSelector.NAME, -1, 0, 0, sortNs));
    }

    public SegmentTreeLabResponse segmentTree(SegmentTreeLabRequest request) {
        int n = request.values().size();
        capValues(n);
        List<TreeOperation> operations = request.operations();
        if (operations.size() > MAX_TREE_OPERATIONS) {
            throw ApiException.inputTooLarge("At most " + MAX_TREE_OPERATIONS + " operations are allowed");
        }
        long queries = operations.stream().filter(op -> isQuery(op.type())).count();
        if (queries * n > MAX_NAIVE_SCAN_WORK) {
            throw ApiException.inputTooLarge("queries × values must be at most " + MAX_NAIVE_SCAN_WORK
                    + " so the naive baseline stays bounded");
        }
        if (n == 0) {
            throw ApiException.validation("values", "must not be empty");
        }
        long[] values = request.values().stream().mapToLong(Long::longValue).toArray();
        for (long value : values) {
            requireTreeValue(value, "values");
        }
        for (int i = 0; i < operations.size(); i++) {
            validateOperation(operations.get(i), n, "operations[" + i + "]");
        }

        long start = System.nanoTime();
        List<Long> treeAnswers = apply(new ChargingAvailabilitySegmentTree(values), operations);
        long treeNs = System.nanoTime() - start;
        start = System.nanoTime();
        List<Long> naiveAnswers = apply(new NaiveRangeScanner(values), operations);
        long naiveNs = System.nanoTime() - start;

        List<QueryResult> results = new ArrayList<>();
        int answer = 0;
        for (int i = 0; i < operations.size(); i++) {
            TreeOperation op = operations.get(i);
            if (isQuery(op.type())) {
                results.add(new QueryResult(i, op.type(), op.from(), op.to(), treeAnswers.get(answer),
                        naiveAnswers.get(answer)));
                answer++;
            }
        }
        return new SegmentTreeLabResponse(n, operations.size(), treeAnswers.equals(naiveAnswers), results,
                new LabTiming("SEGMENT_TREE", treeNs), new LabTiming("NAIVE_SCAN", naiveNs));
    }

    private static MatchingRun runMatching(MatchingAlgorithm algorithm, BipartiteGraph graph,
            MatchingLabRequest request) {
        long start = System.nanoTime();
        MatchingResult result = algorithm.match(graph);
        long runtimeNs = System.nanoTime() - start;
        List<MatchedPair> pairs = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (int u = 0; u < graph.leftCount(); u++) {
            int v = result.matchOfLeft()[u];
            if (v == -1) {
                unmatched.add(request.requests().get(u));
            } else {
                pairs.add(new MatchedPair(request.requests().get(u), request.connectors().get(v)));
            }
        }
        return new MatchingRun(algorithm.name(), result.cardinality(), pairs, unmatched, result.edgesExamined(),
                result.augmentations(), result.reassignments(), MatchingVerifier.violations(graph, result).isEmpty(),
                runtimeNs);
    }

    private static NetworkRun network(NetworkInput input) {
        capVertices(input.nodes().size());
        capEdges(input.edges().size());
        Map<String, Integer> nodes = index(input.nodes(), "network.nodes");
        Integer source = nodes.get(input.source());
        Integer sink = nodes.get(input.sink());
        if (source == null) {
            throw ApiException.validation("network.source", "is not one of the nodes");
        }
        if (sink == null) {
            throw ApiException.validation("network.sink", "is not one of the nodes");
        }
        if (source.equals(sink)) {
            throw ApiException.validation("network.sink", "must differ from the source");
        }
        FlowNetwork network = new FlowNetwork(nodes.size());
        int[] edgeIds = new int[input.edges().size()];
        for (int i = 0; i < edgeIds.length; i++) {
            FlowEdgeInput edge = input.edges().get(i);
            Integer from = nodes.get(edge.from());
            Integer to = nodes.get(edge.to());
            if (from == null || to == null) {
                throw ApiException.validation("network.edges", "edge " + i + " references an unknown node");
            }
            if (from.equals(to)) {
                throw ApiException.validation("network.edges", "edge " + i + " is a self-loop");
            }
            edgeIds[i] = network.addEdge(from, to, edge.capacity());
        }
        long start = System.nanoTime();
        MaxFlowResult result = new EdmondsKarp().maxFlow(network, source, sink);
        long runtimeNs = System.nanoTime() - start;

        Function<Integer, EdgeFlow> view = id -> new EdgeFlow(input.nodes().get(network.from(id)),
                input.nodes().get(network.to(id)), network.capacity(id), network.flow(id));
        List<EdgeFlow> edges = new ArrayList<>();
        for (int id : edgeIds) {
            edges.add(view.apply(id));
        }
        List<EdgeFlow> cut = result.minCutEdges().stream().map(view).toList();
        long cutCapacity = cut.stream().mapToLong(EdgeFlow::capacity).reduce(0, Math::addExact);
        return new NetworkRun(EdmondsKarp.NAME, result.totalFlow(), cutCapacity, edges, cut, result.augmentations(),
                result.bfsRuns(), result.edgesScanned(), runtimeNs);
    }

    private static PowerComparison power(PowerInput input) {
        List<FeederInput> feeders = input.feeders() == null ? List.of() : input.feeders();
        List<FeederLinkInput> links = input.links() == null ? List.of() : input.links();
        capVertices(input.stations().size() + input.sessions().size() + feeders.size());
        capEdges(input.stations().size() + 2 * input.sessions().size() + feeders.size() + links.size());
        List<String> stationIds = input.stations().stream().map(StationInput::id).toList();
        List<String> sessionIds = input.sessions().stream().map(SessionInput::id).toList();
        List<String> feederIds = feeders.stream().map(FeederInput::id).toList();
        Map<String, Integer> stations = index(stationIds, "power.stations");
        index(sessionIds, "power.sessions");
        Map<String, Integer> feederIndex = index(feederIds, "power.feeders");

        int sessions = sessionIds.size();
        int[] sessionStation = new int[sessions];
        long[] connectorMax = new long[sessions];
        long[] requested = new long[sessions];
        long[] vehicleMax = new long[sessions];
        for (int j = 0; j < sessions; j++) {
            SessionInput s = input.sessions().get(j);
            Integer station = stations.get(s.station());
            if (station == null) {
                throw ApiException.validation("power.sessions", "session '" + s.id() + "' references unknown station '"
                        + s.station() + "'");
            }
            sessionStation[j] = station;
            connectorMax[j] = PowerUnits.kwToWatts(s.connectorMaxKw());
            requested[j] = PowerUnits.kwToWatts(s.requestedKw());
            vehicleMax[j] = PowerUnits.kwToWatts(s.vehicleMaxKw());
        }
        long[] stationCapacity = input.stations().stream().mapToLong(s -> PowerUnits.kwToWatts(s.capacityKw()))
                .toArray();

        PowerAllocationProblem problem;
        if (feeders.isEmpty()) {
            if (!links.isEmpty()) {
                throw ApiException.validation("power.links", "links require feeders");
            }
            problem = PowerAllocationProblem.direct(stationCapacity, sessionStation, connectorMax, requested,
                    vehicleMax);
        } else {
            int[] linkFeeder = new int[links.size()];
            int[] linkStation = new int[links.size()];
            for (int i = 0; i < links.size(); i++) {
                Integer feeder = feederIndex.get(links.get(i).feeder());
                Integer station = stations.get(links.get(i).station());
                if (feeder == null || station == null) {
                    throw ApiException.validation("power.links", "link " + i + " references an unknown "
                            + (feeder == null ? "feeder" : "station"));
                }
                linkFeeder[i] = feeder;
                linkStation[i] = station;
            }
            long[] feederCapacity = feeders.stream().mapToLong(f -> PowerUnits.kwToWatts(f.capacityKw())).toArray();
            problem = PowerAllocationProblem.withFeeders(stationCapacity, sessionStation, connectorMax, requested,
                    vehicleMax, new FeederTopology(feederCapacity, linkFeeder, linkStation));
        }
        return new PowerComparison(runPower(new MaxFlowPowerAllocator(), problem, stationIds, sessionIds, feederIds,
                input), runPower(new GreedyPowerAllocator(), problem, stationIds, sessionIds, feederIds, input));
    }

    private static PowerRun runPower(PowerAllocator allocator, PowerAllocationProblem problem, List<String> stationIds,
            List<String> sessionIds, List<String> feederIds, PowerInput input) {
        long start = System.nanoTime();
        PowerAllocationResult result = allocator.allocate(problem);
        long runtimeNs = System.nanoTime() - start;
        List<StationPower> stations = new ArrayList<>();
        for (int i = 0; i < stationIds.size(); i++) {
            stations.add(new StationPower(stationIds.get(i), PowerUnits.wattsToKw(result.stationAllocatedW()[i]),
                    PowerUnits.wattsToKw(result.stationRemainingW()[i])));
        }
        List<SessionPower> sessions = new ArrayList<>();
        for (int j = 0; j < sessionIds.size(); j++) {
            sessions.add(new SessionPower(sessionIds.get(j), input.sessions().get(j).station(),
                    PowerUnits.wattsToKw(result.sessionAllocatedW()[j])));
        }
        List<LabBottleneck> bottlenecks = result.bottlenecks().stream().map(b -> new LabBottleneck(b.kind(),
                switch (b.kind()) {
                    case FEEDER -> feederIds.get(b.index());
                    case STATION_GRID -> stationIds.get(b.index());
                    case CONNECTOR, SESSION_DEMAND -> sessionIds.get(b.index());
                }, PowerUnits.wattsToKw(b.capacityW()))).toList();
        return new PowerRun(allocator.name(), PowerUnits.wattsToKw(result.totalRequestedW()),
                PowerUnits.wattsToKw(result.totalAllocatedW()), PowerUnits.wattsToKw(result.unservedW()), stations,
                sessions, bottlenecks, result.operations(), CapacityVerifier.violations(problem, result), runtimeNs);
    }

    private static boolean isQuery(OperationType type) {
        return type == OperationType.SUM || type == OperationType.MAX;
    }

    private static void validateOperation(TreeOperation op, int n, String field) {
        if (isQuery(op.type())) {
            if (op.from() == null || op.to() == null) {
                throw ApiException.validation(field, op.type() + " needs from and to");
            }
            if (op.from() < 0 || op.to() >= n || op.from() > op.to()) {
                throw ApiException.validation(field, "range must satisfy 0 <= from <= to < " + n);
            }
        } else {
            if (op.index() == null || op.value() == null) {
                throw ApiException.validation(field, op.type() + " needs index and value");
            }
            if (op.index() < 0 || op.index() >= n) {
                throw ApiException.validation(field, "index must be in [0, " + n + ")");
            }
            requireTreeValue(op.value(), field);
        }
    }

    private static void requireTreeValue(long value, String field) {
        if (Math.abs(value) > MAX_ABS_TREE_VALUE) {
            throw ApiException.validation(field, "values must be within ±" + MAX_ABS_TREE_VALUE);
        }
    }

    /** Runs the operations in order and returns the answers to the range queries. */
    private static List<Long> apply(RangeAggregate aggregate, List<TreeOperation> operations) {
        List<Long> answers = new ArrayList<>();
        for (TreeOperation op : operations) {
            switch (op.type()) {
                case SUM -> answers.add(aggregate.rangeSum(op.from(), op.to()));
                case MAX -> answers.add(aggregate.rangeMax(op.from(), op.to()));
                case SET -> aggregate.set(op.index(), op.value());
                case ADD -> aggregate.add(op.index(), op.value());
            }
        }
        return answers;
    }

    /** Position of each id; a repeated id is a 400 naming {@code field}. */
    private static Map<String, Integer> index(List<String> ids, String field) {
        Map<String, Integer> index = new HashMap<>();
        for (String id : ids) {
            if (index.putIfAbsent(id, index.size()) != null) {
                throw ApiException.validation(field, "duplicate id '" + id + "'");
            }
        }
        return index;
    }

    private static void capVertices(int vertices) {
        if (vertices > MAX_VERTICES) {
            throw ApiException.inputTooLarge("At most " + MAX_VERTICES + " vertices are allowed, got " + vertices);
        }
    }

    private static void capEdges(int edges) {
        if (edges > MAX_EDGES) {
            throw ApiException.inputTooLarge("At most " + MAX_EDGES + " edges are allowed, got " + edges);
        }
    }

    private static void capValues(int values) {
        if (values > MAX_VALUES) {
            throw ApiException.inputTooLarge("At most " + MAX_VALUES + " values are allowed, got " + values);
        }
    }
}
