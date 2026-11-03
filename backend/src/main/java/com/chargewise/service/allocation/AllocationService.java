package com.chargewise.service.allocation;

import com.chargewise.algorithm.matching.BipartiteMatcher;
import com.chargewise.algorithm.matching.MatchingResult;
import com.chargewise.algorithm.power.Bottleneck;
import com.chargewise.algorithm.power.CapacityVerifier;
import com.chargewise.algorithm.power.MaxFlowPowerAllocator;
import com.chargewise.algorithm.power.PowerAllocationResult;
import com.chargewise.algorithm.power.PowerUnits;
import com.chargewise.algorithm.selection.Percentiles;
import com.chargewise.algorithm.selection.RandomizedQuickselect;
import com.chargewise.algorithm.selection.SelectionResult;
import com.chargewise.dto.AllocationRunRequest;
import com.chargewise.dto.AllocationRunResponse;
import com.chargewise.dto.AllocationRunResult;
import com.chargewise.dto.AllocationRunResult.Assignment;
import com.chargewise.dto.AllocationRunResult.BottleneckView;
import com.chargewise.dto.AllocationRunResult.Metrics;
import com.chargewise.dto.AllocationRunResult.RequestedPower;
import com.chargewise.dto.AllocationRunResult.StationCapacity;
import com.chargewise.dto.AllocationRunResult.Unassigned;
import com.chargewise.entity.AllocationRun;
import com.chargewise.entity.Booking;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.PowerAllocation;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import com.chargewise.entity.StatusReason;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.AllocationRunRepository;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ChargingSessionRepository;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.PowerAllocationRepository;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.TimeSlots;
import com.chargewise.service.allocation.ConnectorState.Window;
import com.chargewise.service.allocation.PowerProblemBuilder.Built;
import com.chargewise.service.allocation.PowerProblemBuilder.CommittedLoad;
import com.chargewise.service.allocation.PowerProblemBuilder.NewSession;
import com.chargewise.service.allocation.PowerProblemBuilder.StationGrid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The allocation pipeline of architecture D4, run as one transaction: any failure, including a capacity
 * violation found by the independent verifier, rolls back every booking, session and the run record itself.
 */
@Service
public class AllocationService {

    static final Duration DEFAULT_INTERVAL = Duration.ofDays(15);
    private static final double[] PERCENTILES = {0.50, 0.75, 0.90};

    private final BookingRepository bookings;
    private final ConnectorRepository connectors;
    private final ChargingSessionRepository sessions;
    private final PowerAllocationRepository powerAllocations;
    private final AllocationRunRepository runs;
    private final UserRepository users;
    private final AllocationLock lock;
    private final JsonMapper json;
    private final Clock clock;

    public AllocationService(BookingRepository bookings, ConnectorRepository connectors,
            ChargingSessionRepository sessions, PowerAllocationRepository powerAllocations,
            AllocationRunRepository runs, UserRepository users, AllocationLock lock, JsonMapper json, Clock clock) {
        this.bookings = bookings;
        this.connectors = connectors;
        this.sessions = sessions;
        this.powerAllocations = powerAllocations;
        this.runs = runs;
        this.users = users;
        this.lock = lock;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public AllocationRunResponse run(AllocationRunRequest request, Long operatorId) {
        long started = System.nanoTime();
        Instant now = clock.instant();
        Instant from = request.intervalStart() != null ? request.intervalStart() : TimeSlots.floor(now);
        Instant to = request.intervalEnd() != null ? request.intervalEnd() : from.plus(DEFAULT_INTERVAL);
        validateInterval(from, to);

        lock.acquire();
        AllocationRun run = runs.save(new AllocationRun(from, to, users.getReferenceById(operatorId), "{}", now));

        List<Booking> eligible = new ArrayList<>(bookings.findEligibleForAllocation(from, to));
        // Requests whose window began before any run reached them would otherwise stay queued for ever: they fall
        // outside every later interval. They join this run only to be rejected as WINDOW_EXPIRED below.
        Set<Long> inInterval = new LinkedHashSet<>();
        eligible.forEach(b -> inInterval.add(b.getId()));
        bookings.findQueuedStartedBy(now).stream().filter(b -> !inInterval.contains(b.getId())).forEach(eligible::add);
        List<Unassigned> unassigned = new ArrayList<>();
        List<Booking> valid = new ArrayList<>();
        for (Booking booking : eligible) {
            StatusReason invalid = !booking.getRequestedStart().isAfter(now) ? StatusReason.WINDOW_EXPIRED
                    : booking.getRequestedEnergyKwh().compareTo(booking.getVehicle().batteryHeadroomKwh()) > 0
                            ? StatusReason.ENERGY_EXCEEDS_BATTERY_HEADROOM : null;
            if (invalid == null) {
                valid.add(booking);
            } else {
                unassigned.add(decline(booking, invalid));
            }
        }

        List<BookingCandidate> candidates = valid.stream().map(AllocationService::candidate).toList();
        long selectionStart = System.nanoTime();
        RequestedPower requestedPower = requestedPower(candidates, from.getEpochSecond());
        long selectionNs = System.nanoTime() - selectionStart;

        Set<Long> stationIds = new LinkedHashSet<>();
        valid.forEach(b -> stationIds.add(b.getStation().getId()));
        List<Connector> connectorRows = stationIds.isEmpty() ? List.of()
                : connectors.findAllByStationIdInOrderByIdAsc(stationIds);
        CompatibilityGraph graph = CompatibilityGraphBuilder.build(candidates,
                connectorStates(connectorRows, stationIds, candidates));

        long matchingStart = System.nanoTime();
        MatchingResult matching = new BipartiteMatcher().match(graph.graph());
        long matchingNs = System.nanoTime() - matchingStart;

        List<Integer> matchedLeft = new ArrayList<>();
        List<NewSession> newSessions = new ArrayList<>();
        for (int u = 0; u < candidates.size(); u++) {
            StatusReason reason = graph.reasonFor(u, matching);
            if (reason != null) {
                unassigned.add(decline(valid.get(u), reason));
                continue;
            }
            BookingCandidate c = candidates.get(u);
            Connector connector = connectorRows.get(matching.matchOfLeft()[u]);
            matchedLeft.add(u);
            newSessions.add(new NewSession(c.stationId(), c.start(), c.end(),
                    PowerUnits.kwToWatts(connector.getMaxPowerKw()), c.requestedW(), c.vehicleMaxW()));
        }

        long powerStart = System.nanoTime();
        Built built = null;
        PowerAllocationResult power = null;
        if (!newSessions.isEmpty()) {
            built = PowerProblemBuilder.build(newSessions, stationGrids(newSessions, valid, matchedLeft));
            power = new MaxFlowPowerAllocator().allocate(built.problem());
            List<String> violations = CapacityVerifier.violations(built.problem(), power);
            if (!violations.isEmpty()) {
                throw new IllegalStateException("Allocation violates capacity limits: " + violations);
            }
        }
        long powerNs = System.nanoTime() - powerStart;

        Map<Long, ChargingSession> previousSessions = new HashMap<>();
        if (!matchedLeft.isEmpty()) {
            sessions.findAllByBookingIdIn(matchedLeft.stream().map(u -> valid.get(u).getId()).toList())
                    .forEach(s -> previousSessions.put(s.getBooking().getId(), s));
        }
        List<Assignment> assignments = new ArrayList<>();
        for (int j = 0; j < matchedLeft.size(); j++) {
            int u = matchedLeft.get(j);
            Booking booking = valid.get(u);
            Connector connector = connectorRows.get(matching.matchOfLeft()[u]);
            BigDecimal allocatedKw = PowerUnits.wattsToKw(power.sessionAllocatedW()[j]);
            if (allocatedKw.signum() == 0) {
                unassigned.add(decline(booking, StatusReason.INSUFFICIENT_GRID_CAPACITY));
                continue;
            }
            BigDecimal requestedKw = PowerUnits.wattsToKw(newSessions.get(j).requestedW());
            booking.assign(connector, run, now);
            ChargingSession session = previousSessions.get(booking.getId());
            if (session == null) {
                session = new ChargingSession(booking, booking.getStation(), connector, allocatedKw);
            } else {
                session.reschedule(connector, allocatedKw);
            }
            session = sessions.save(session);
            powerAllocations.save(new PowerAllocation(session, requestedKw, allocatedKw, now));
            assignments.add(new Assignment(booking.getId(), booking.getStation().getId(), connector.getId(),
                    session.getId(), requestedKw, allocatedKw));
        }
        // Surface an exclusion-constraint violation (D6 backstop) here, inside the transaction.
        bookings.flush();

        AllocationRunResult result = new AllocationRunResult(BipartiteMatcher.NAME, MaxFlowPowerAllocator.NAME,
                RandomizedQuickselect.NAME, eligible.size(), assignments.size(),
                count(unassigned, BookingStatus.WAITLISTED), count(unassigned, BookingStatus.REJECTED), assignments,
                unassigned, stationCapacities(stationIds, valid, candidates, built, power),
                bottlenecks(built, power, valid, matchedLeft), requestedPower,
                new Metrics(graph.graph().edgeCount(), matching.cardinality(), matching.edgesExamined(),
                        matching.augmentations(), power == null ? 0 : power.operations(),
                        kw(power == null ? 0 : power.totalRequestedW()), kw(power == null ? 0 : power.totalAllocatedW()),
                        kw(power == null ? 0 : power.unservedW()), selectionNs, matchingNs, powerNs,
                        System.nanoTime() - started));
        run.setResult(json.writeValueAsString(result));
        return new AllocationRunResponse(run.getId(), from, to, now, result);
    }

    @Transactional(readOnly = true)
    public AllocationRunResponse get(Long id) {
        AllocationRun run = runs.findById(id).orElseThrow(() -> ApiException.notFound("Allocation run"));
        return new AllocationRunResponse(run.getId(), run.getIntervalStart(), run.getIntervalEnd(),
                run.getCreatedAt(), json.readValue(run.getResult(), AllocationRunResult.class));
    }

    private static void validateInterval(Instant from, Instant to) {
        if (!TimeSlots.isAligned(from)) {
            throw ApiException.validation("intervalStart", "must be on a 15-minute boundary");
        }
        if (!TimeSlots.isAligned(to)) {
            throw ApiException.validation("intervalEnd", "must be on a 15-minute boundary");
        }
        if (!to.isAfter(from)) {
            throw ApiException.validation("intervalEnd", "must be after intervalStart");
        }
        if (Duration.between(from, to).compareTo(DEFAULT_INTERVAL) > 0) {
            throw ApiException.validation("intervalEnd", "interval must be at most 15 days");
        }
    }

    private static BookingCandidate candidate(Booking b) {
        return new BookingCandidate(b.getId(), b.getStation().getId(), b.getVehicle().getConnectorTypes(),
                BookingPower.vehicleMaxW(b), BookingPower.wattHours(b.getRequestedEnergyKwh()),
                b.getRequestedStart(), b.getRequestedEnd());
    }

    /** Structural reasons reject the booking; temporary ones waitlist it so a later run can retry (D3). */
    private static Unassigned decline(Booking booking, StatusReason reason) {
        if (reason.isStructural()) {
            booking.reject(reason);
        } else {
            booking.waitlist(reason);
        }
        return new Unassigned(booking.getId(), booking.getStation().getId(), booking.getStatus(), reason);
    }

    private static int count(List<Unassigned> unassigned, BookingStatus status) {
        return (int) unassigned.stream().filter(u -> u.status() == status).count();
    }

    private static BigDecimal kw(long watts) {
        return PowerUnits.wattsToKw(watts);
    }

    /** D4 step 4: nearest-rank p50/p75/p90 of requested power, with the Quickselect work it took. */
    private static RequestedPower requestedPower(List<BookingCandidate> candidates, long seed) {
        if (candidates.isEmpty()) {
            return null;
        }
        long[] working = candidates.stream().mapToLong(BookingCandidate::requestedW).toArray();
        RandomizedQuickselect quickselect = new RandomizedQuickselect(new SplittableRandom(seed));
        BigDecimal[] values = new BigDecimal[PERCENTILES.length];
        long comparisons = 0;
        long swaps = 0;
        for (int i = 0; i < PERCENTILES.length; i++) {
            SelectionResult selected = quickselect.select(working,
                    Percentiles.nearestRankIndex(PERCENTILES[i], working.length));
            values[i] = kw(selected.value());
            comparisons += selected.comparisons();
            swaps += selected.swaps();
        }
        return new RequestedPower(values[0], values[1], values[2], seed, comparisons, swaps);
    }

    private List<ConnectorState> connectorStates(List<Connector> rows, Set<Long> stationIds,
            List<BookingCandidate> candidates) {
        Map<Long, List<Window>> assigned = new HashMap<>();
        if (!candidates.isEmpty()) {
            Instant horizonStart = candidates.stream().map(BookingCandidate::start).min(Instant::compareTo).get();
            Instant horizonEnd = candidates.stream().map(BookingCandidate::end).max(Instant::compareTo).get();
            for (Booking b : bookings.findAssignedOverlapping(stationIds, horizonStart, horizonEnd)) {
                assigned.computeIfAbsent(b.getConnector().getId(), k -> new ArrayList<>())
                        .add(new Window(b.getRequestedStart(), b.getRequestedEnd()));
            }
        }
        return rows.stream().map(c -> new ConnectorState(c.getId(), c.getStation().getId(), c.getConnectorType(),
                PowerUnits.kwToWatts(c.getMaxPowerKw()), c.getStation().getStatus() == StationStatus.OPERATIONAL,
                c.getStatus() == ConnectorStatus.OPERATIONAL, assigned.getOrDefault(c.getId(), List.of()))).toList();
    }

    /** Grid limits and committed SCHEDULED/ACTIVE load for every station with a matched session. */
    private Map<Long, StationGrid> stationGrids(List<NewSession> newSessions, List<Booking> valid,
            List<Integer> matchedLeft) {
        Map<Long, Station> stations = new LinkedHashMap<>();
        matchedLeft.forEach(u -> stations.putIfAbsent(valid.get(u).getStation().getId(), valid.get(u).getStation()));
        Instant horizonStart = newSessions.stream().map(NewSession::start).min(Instant::compareTo).get();
        Instant horizonEnd = newSessions.stream().map(NewSession::end).max(Instant::compareTo).get();
        Map<Long, List<CommittedLoad>> committed = new HashMap<>();
        for (ChargingSession s : sessions.findCommitted(stations.keySet(), horizonStart, horizonEnd)) {
            committed.computeIfAbsent(s.getStation().getId(), k -> new ArrayList<>()).add(new CommittedLoad(
                    s.getBooking().getRequestedStart(), s.getBooking().getRequestedEnd(),
                    PowerUnits.kwToWatts(s.getAllocatedPowerKw())));
        }
        Map<Long, StationGrid> grids = new HashMap<>();
        stations.forEach((id, station) -> grids.put(id, new StationGrid(id,
                PowerUnits.kwToWatts(station.getGridCapacityKw()), committed.getOrDefault(id, List.of()))));
        return grids;
    }

    private static List<StationCapacity> stationCapacities(Set<Long> stationIds, List<Booking> valid,
            List<BookingCandidate> candidates, Built built, PowerAllocationResult power) {
        Map<Long, Integer> problemIndex = new HashMap<>();
        if (built != null) {
            for (int i = 0; i < built.stationIds().length; i++) {
                problemIndex.put(built.stationIds()[i], i);
            }
        }
        List<StationCapacity> result = new ArrayList<>();
        for (long stationId : stationIds) {
            Station station = valid.stream().map(Booking::getStation).filter(s -> s.getId() == stationId)
                    .findFirst().orElseThrow();
            long demandW = candidates.stream().filter(c -> c.stationId() == stationId)
                    .mapToLong(BookingCandidate::requestedW).reduce(0, Math::addExact);
            long gridW = PowerUnits.kwToWatts(station.getGridCapacityKw());
            Double ratio = gridW == 0 ? null
                    : BigDecimal.valueOf(demandW).divide(BigDecimal.valueOf(gridW), 3, RoundingMode.HALF_UP)
                            .doubleValue();
            Integer i = problemIndex.get(stationId);
            result.add(new StationCapacity(stationId, station.getGridCapacityKw(), kw(demandW), ratio,
                    i == null ? null : kw(built.committedPeakW()[i]), i == null ? null : kw(built.availableW()[i]),
                    i == null ? null : kw(power.stationAllocatedW()[i])));
        }
        return result;
    }

    private static List<BottleneckView> bottlenecks(Built built, PowerAllocationResult power, List<Booking> valid,
            List<Integer> matchedLeft) {
        if (power == null) {
            return List.of();
        }
        return power.bottlenecks().stream().map(b -> b.kind() == Bottleneck.Kind.STATION_GRID
                ? new BottleneckView(b.kind(), built.stationIds()[b.index()], null, kw(b.capacityW()))
                : new BottleneckView(b.kind(), null, valid.get(matchedLeft.get(b.index())).getId(),
                        kw(b.capacityW()))).toList();
    }
}
