package com.chargewise.service;

import com.chargewise.algorithm.selection.Percentiles;
import com.chargewise.algorithm.selection.RandomizedQuickselect;
import com.chargewise.dto.DashboardResponse;
import com.chargewise.dto.DashboardResponse.Utilisation;
import com.chargewise.dto.DashboardResponse.WaitingTime;
import com.chargewise.entity.AllocationRun;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.SessionStatus;
import com.chargewise.entity.StationStatus;
import com.chargewise.repository.AllocationRunRepository;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ChargingSessionRepository;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.StationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    /** Fixed Quickselect seed (D9): the selected values never depend on it, only the operation counts do. */
    static final long SELECTION_SEED = 11L;
    private static final double[] P50_P90 = {0.50, 0.90};

    private final StationRepository stations;
    private final ConnectorRepository connectors;
    private final BookingRepository bookings;
    private final ChargingSessionRepository sessions;
    private final AllocationRunRepository runs;
    private final Clock clock;

    public DashboardService(StationRepository stations, ConnectorRepository connectors, BookingRepository bookings,
            ChargingSessionRepository sessions, AllocationRunRepository runs, Clock clock) {
        this.stations = stations;
        this.connectors = connectors;
        this.bookings = bookings;
        this.sessions = sessions;
        this.runs = runs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Instant now = clock.instant();
        Map<SessionStatus, Long> sessionCounts = counts(SessionStatus.class, sessions.countByStatus());
        long active = sessionCounts.get(SessionStatus.ACTIVE);
        long usable = connectors.countUsable();
        BigDecimal activeKw = sessions.activePowerKw();
        BigDecimal gridKw = stations.operationalGridKw();
        Utilisation utilisation = new Utilisation(active, usable, usable == 0 ? null : ratio(active, usable),
                activeKw, gridKw, gridKw.signum() == 0 ? null
                        : activeKw.divide(gridKw, 3, RoundingMode.HALF_UP).doubleValue());

        long[] assignWaits = bookings.assignWaitSeconds().stream().mapToLong(s -> Math.max(0, s)).toArray();
        long[] queueAges = bookings.queuedSince().stream()
                .mapToLong(created -> Math.max(0, Duration.between(created, now).toSeconds())).toArray();
        long[] waitPercentiles = percentiles(assignWaits);
        long[] agePercentiles = percentiles(queueAges);
        WaitingTime waiting = new WaitingTime(RandomizedQuickselect.NAME, assignWaits.length,
                at(waitPercentiles, 0), at(waitPercentiles, 1), queueAges.length, at(agePercentiles, 0),
                at(agePercentiles, 1));

        return new DashboardResponse(counts(StationStatus.class, stations.countByStatus()),
                counts(ConnectorStatus.class, connectors.countByStatus()),
                counts(BookingStatus.class, bookings.countByStatus()), sessionCounts, utilisation, waiting,
                runs.findFirstByOrderByCreatedAtDescIdDesc().map(AllocationRun::getId).orElse(null), now);
    }

    private static <E extends Enum<E>> Map<E, Long> counts(Class<E> type, List<Object[]> rows) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0L);
        }
        for (Object[] row : rows) {
            counts.put(type.cast(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static double ratio(long part, long whole) {
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 3, RoundingMode.HALF_UP).doubleValue();
    }

    private static long[] percentiles(long[] values) {
        return values.length == 0 ? null
                : Percentiles.compute(values, P50_P90, new SplittableRandom(SELECTION_SEED));
    }

    private static Long at(long[] values, int i) {
        return values == null ? null : values[i];
    }
}
