package com.chargewise.service;

import com.chargewise.dto.simulation.SimulationComparisonResponse;
import com.chargewise.dto.simulation.SimulationComparisonResponse.Better;
import com.chargewise.dto.simulation.SimulationComparisonResponse.Row;
import com.chargewise.dto.simulation.SimulationRequest;
import com.chargewise.dto.simulation.SimulationResponse;
import com.chargewise.dto.simulation.SimulationResultResponse;
import com.chargewise.dto.simulation.SimulationRunResponse;
import com.chargewise.entity.RunStatus;
import com.chargewise.entity.Simulation;
import com.chargewise.entity.SimulationResult;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.SimulationRepository;
import com.chargewise.repository.SimulationResultRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.simulation.ScenarioGenerator;
import com.chargewise.simulation.ScenarioInput;
import com.chargewise.simulation.SimulationConfig;
import com.chargewise.simulation.SimulationExecutor;
import com.chargewise.simulation.SimulationMetrics;
import com.chargewise.simulation.Strategy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores seeded scenarios and runs each one twice on the identical generated input: once with the algorithms and once
 * with the greedy baselines (architecture D10). A re-run replaces the stored results and reports whether it reproduced
 * them (D9).
 */
@Service
public class SimulationService {

    /** What {@code simulation_results.input_summary} holds: enough to identify the generated input. */
    record InputSummary(String inputHash, String canonicalVersion, int stations, int connectors, int requests,
            int horizonSlots, int feeders, int feederLinks, int connectorOutages, int stationOutages,
            int gridReductions) {
    }

    /** What {@code simulation_results.output_summary} holds: the headline outcome of one strategy. */
    record OutputSummary(Strategy strategy, int matchedCount, int unmatchedCount, BigDecimal deliveredEnergyKwh,
            BigDecimal totalAllocatedKw, int capacityViolations) {
    }

    private final SimulationRepository simulations;
    private final SimulationResultRepository results;
    private final UserRepository users;
    private final JsonMapper json;
    private final Clock clock;

    public SimulationService(SimulationRepository simulations, SimulationResultRepository results,
            UserRepository users, JsonMapper json, Clock clock) {
        this.simulations = simulations;
        this.results = results;
        this.users = users;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public SimulationResponse create(SimulationRequest request, Long operatorId) {
        SimulationConfig preset = SimulationConfig.preset(request.scenario());
        SimulationConfig config = new SimulationConfig(request.scenario(),
                orDefault(request.stations(), preset.stations()),
                orDefault(request.connectorsPerStation(), preset.connectorsPerStation()),
                orDefault(request.requests(), preset.requests()),
                orDefault(request.horizonSlots(), preset.horizonSlots()),
                orDefault(request.maxWaitSlots(), preset.maxWaitSlots()));
        Simulation simulation = simulations.save(new Simulation(users.getReferenceById(operatorId),
                request.name().trim(), request.seed(), json.writeValueAsString(config), RunStatus.PENDING,
                Instant.now(clock)));
        return toResponse(simulation);
    }

    @Transactional(readOnly = true)
    public List<SimulationResponse> list() {
        return simulations.findTop50ByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public SimulationRunResponse run(Long id) {
        Simulation simulation = simulations.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Simulation"));
        SimulationConfig config = config(simulation);
        List<SimulationResult> previous = results.findAllBySimulationIdOrderByIdAsc(id);

        ScenarioInput input = ScenarioGenerator.generate(config, simulation.getRandomSeed());
        String inputHash = input.inputHash();
        SimulationMetrics algorithm = SimulationExecutor.run(input, Strategy.ALGORITHM);
        SimulationMetrics baseline = SimulationExecutor.run(input, Strategy.BASELINE);

        Boolean reproduced = previous.isEmpty() ? null : reproduces(previous, inputHash, algorithm, baseline);
        results.deleteAll(previous);
        results.flush();
        Instant now = Instant.now(clock);
        String inputSummary = json.writeValueAsString(summary(input, inputHash));
        for (SimulationMetrics metrics : List.of(algorithm, baseline)) {
            results.save(new SimulationResult(simulation, metrics.matchingAlgorithm() + "+" + metrics.powerAlgorithm(),
                    SimulationExecutor.VERSION, inputSummary, json.writeValueAsString(outputSummary(metrics)),
                    json.writeValueAsString(metrics), metrics.totalNs(), now));
        }
        simulation.setStatus(RunStatus.COMPLETED);
        return new SimulationRunResponse(toResponse(simulation), inputHash, reproduced);
    }

    @Transactional(readOnly = true)
    public List<SimulationResultResponse> results(Long id) {
        simulations.findById(id).orElseThrow(() -> ApiException.notFound("Simulation"));
        return results.findAllBySimulationIdOrderByIdAsc(id).stream().map(this::toResultResponse).toList();
    }

    @Transactional(readOnly = true)
    public SimulationComparisonResponse comparison(Long id) {
        Simulation simulation = simulations.findById(id).orElseThrow(() -> ApiException.notFound("Simulation"));
        List<SimulationResultResponse> stored = results.findAllBySimulationIdOrderByIdAsc(id).stream()
                .map(this::toResultResponse).toList();
        SimulationResultResponse algorithm = byStrategy(stored, Strategy.ALGORITHM);
        SimulationResultResponse baseline = byStrategy(stored, Strategy.BASELINE);
        if (algorithm == null || baseline == null) {
            throw ApiException.invalidState("Run the simulation before comparing its results");
        }
        return new SimulationComparisonResponse(simulation.getId(), simulation.getName(), config(simulation).scenario(),
                simulation.getRandomSeed(), algorithm.inputHash(), algorithm.metrics(), baseline.metrics(),
                rows(algorithm.metrics(), baseline.metrics()));
    }

    private boolean reproduces(List<SimulationResult> previous, String inputHash, SimulationMetrics algorithm,
            SimulationMetrics baseline) {
        List<SimulationResultResponse> stored = previous.stream().map(this::toResultResponse).toList();
        SimulationResultResponse oldAlgorithm = byStrategy(stored, Strategy.ALGORITHM);
        SimulationResultResponse oldBaseline = byStrategy(stored, Strategy.BASELINE);
        return oldAlgorithm != null && oldBaseline != null
                && inputHash.equals(oldAlgorithm.inputHash()) && inputHash.equals(oldBaseline.inputHash())
                && algorithm.withoutTimings().equals(oldAlgorithm.metrics().withoutTimings())
                && baseline.withoutTimings().equals(oldBaseline.metrics().withoutTimings());
    }

    private static SimulationResultResponse byStrategy(List<SimulationResultResponse> stored, Strategy strategy) {
        return stored.stream().filter(result -> result.strategy() == strategy).findFirst().orElse(null);
    }

    private static List<Row> rows(SimulationMetrics algorithm, SimulationMetrics baseline) {
        List<Row> rows = new ArrayList<>();
        rows.add(row("matchedCount", "Requests started", "requests", algorithm, baseline,
                m -> BigDecimal.valueOf(m.matchedCount()), Better.HIGHER));
        rows.add(row("expiredCount", "Requests expired", "requests", algorithm, baseline,
                m -> BigDecimal.valueOf(m.expiredCount()), Better.LOWER));
        rows.add(row("completedSessions", "Sessions completed", "sessions", algorithm, baseline,
                m -> BigDecimal.valueOf(m.completedSessions()), Better.HIGHER));
        rows.add(row("deliveredEnergyKwh", "Energy delivered", "kWh", algorithm, baseline,
                SimulationMetrics::deliveredEnergyKwh, Better.HIGHER));
        rows.add(row("totalAllocatedKw", "Power allocated, summed over slots", "kW", algorithm, baseline,
                SimulationMetrics::totalAllocatedKw, Better.HIGHER));
        rows.add(row("unservedKw", "Unserved power, summed over slots", "kW", algorithm, baseline,
                SimulationMetrics::unservedKw, Better.LOWER));
        rows.add(row("stationUtilisation", "Connector utilisation", "ratio", algorithm, baseline,
                m -> BigDecimal.valueOf(m.stationUtilisation()).setScale(4, java.math.RoundingMode.HALF_EVEN),
                Better.HIGHER));
        rows.add(row("waitP50Minutes", "Median wait to start", "min", algorithm, baseline,
                m -> m.waitP50Minutes() == null ? null : BigDecimal.valueOf(m.waitP50Minutes()), Better.LOWER));
        rows.add(row("waitP90Minutes", "p90 wait to start", "min", algorithm, baseline,
                m -> m.waitP90Minutes() == null ? null : BigDecimal.valueOf(m.waitP90Minutes()), Better.LOWER));
        rows.add(row("capacityViolations", "Capacity violations", "count", algorithm, baseline,
                m -> BigDecimal.valueOf(m.capacityViolations()), Better.LOWER));
        return rows;
    }

    private static Row row(String metric, String label, String unit, SimulationMetrics algorithm,
            SimulationMetrics baseline, Function<SimulationMetrics, BigDecimal> value, Better better) {
        BigDecimal a = value.apply(algorithm);
        BigDecimal b = value.apply(baseline);
        return new Row(metric, label, unit, a, b, a == null || b == null ? null : a.subtract(b), better);
    }

    private static InputSummary summary(ScenarioInput input, String inputHash) {
        return new InputSummary(inputHash, ScenarioInput.CANONICAL_VERSION, input.stations().size(),
                input.connectors().size(), input.requests().size(), input.config().horizonSlots(),
                input.feeders() == null ? 0 : input.feeders().feederCount(),
                input.feeders() == null ? 0 : input.feeders().linkCount(), input.connectorOutages().size(),
                input.stationOutages().size(), input.gridReductions().size());
    }

    private static OutputSummary outputSummary(SimulationMetrics metrics) {
        return new OutputSummary(metrics.strategy(), metrics.matchedCount(), metrics.unmatchedCount(),
                metrics.deliveredEnergyKwh(), metrics.totalAllocatedKw(), metrics.capacityViolations());
    }

    private SimulationResultResponse toResultResponse(SimulationResult result) {
        SimulationMetrics metrics = json.readValue(result.getMetrics(), SimulationMetrics.class);
        InputSummary input = json.readValue(result.getInputSummary(), InputSummary.class);
        return new SimulationResultResponse(result.getId(), metrics.strategy(), result.getAlgorithmName(),
                result.getAlgorithmVersion(), input.inputHash(), metrics, result.getRuntimeNs(), result.getCreatedAt());
    }

    private SimulationConfig config(Simulation simulation) {
        return json.readValue(simulation.getConfiguration(), SimulationConfig.class);
    }

    private SimulationResponse toResponse(Simulation simulation) {
        return new SimulationResponse(simulation.getId(), simulation.getName(), simulation.getRandomSeed(),
                config(simulation), simulation.getStatus(), simulation.getCreatedAt());
    }

    private static int orDefault(Integer value, int preset) {
        return value == null ? preset : value;
    }
}
