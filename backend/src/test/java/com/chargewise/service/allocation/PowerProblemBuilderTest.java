package com.chargewise.service.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargewise.service.allocation.PowerProblemBuilder.Built;
import com.chargewise.service.allocation.PowerProblemBuilder.CommittedLoad;
import com.chargewise.service.allocation.PowerProblemBuilder.NewSession;
import com.chargewise.service.allocation.PowerProblemBuilder.StationGrid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PowerProblemBuilderTest {

    private static final Instant T = Instant.parse("2026-11-05T10:00:00Z");

    private static Instant at(int minutes) {
        return T.plus(Duration.ofMinutes(minutes));
    }

    @Test
    void requestedPowerIsTheCeilingOfTheAverageCappedByTheVehicle() {
        // 10 kWh over 45 min = 13 333.33 W, rounded up.
        assertThat(PowerProblemBuilder.requestedW(10_000, 45, 50_000)).isEqualTo(13_334);
        assertThat(PowerProblemBuilder.requestedW(20_000, 60, 50_000)).isEqualTo(20_000);
        // 40 kWh in 30 min needs 80 kW; the vehicle accepts 50 kW.
        assertThat(PowerProblemBuilder.requestedW(40_000, 30, 50_000)).isEqualTo(50_000);
    }

    @Test
    void committedPeakInsideTheHorizonIsSubtractedFromTheGrid() {
        List<CommittedLoad> committed = List.of(
                new CommittedLoad(at(0), at(60), 30_000),
                new CommittedLoad(at(30), at(90), 25_000), // overlaps the first in [30, 60): 55 kW peak
                new CommittedLoad(at(120), at(180), 70_000)); // outside the horizon
        assertThat(PowerProblemBuilder.committedPeakW(committed, at(0), at(120))).isEqualTo(55_000);
        assertThat(PowerProblemBuilder.availableW(120_000, committed, at(0), at(120))).isEqualTo(65_000);
    }

    @Test
    void loadEndingAtTheHorizonStartDoesNotCount() {
        List<CommittedLoad> committed = List.of(new CommittedLoad(at(-60), at(0), 90_000));
        assertThat(PowerProblemBuilder.availableW(100_000, committed, at(0), at(60))).isEqualTo(100_000);
    }

    @Test
    void availabilityIsFlooredAtZero() {
        // A grid reduced below what was already committed leaves nothing, never a negative capacity.
        List<CommittedLoad> committed = List.of(new CommittedLoad(at(0), at(60), 80_000));
        assertThat(PowerProblemBuilder.availableW(50_000, committed, at(0), at(60))).isZero();
    }

    @Test
    void buildGroupsSessionsByStationOverEachStationsOwnHorizon() {
        Map<Long, StationGrid> grids = Map.of(
                7L, new StationGrid(7, 100_000, List.of(new CommittedLoad(at(0), at(30), 40_000))),
                8L, new StationGrid(8, 50_000, List.of(new CommittedLoad(at(0), at(30), 40_000))));
        List<NewSession> sessions = List.of(
                new NewSession(7, at(15), at(60), 60_000, 20_000, 50_000),
                new NewSession(8, at(60), at(120), 22_000, 11_000, 11_000), // after station 8's committed load
                new NewSession(7, at(0), at(15), 60_000, 30_000, 50_000));

        Built built = PowerProblemBuilder.build(sessions, grids);

        assertThat(built.stationIds()).containsExactly(7L, 8L);
        assertThat(built.committedPeakW()).containsExactly(40_000L, 0L);
        assertThat(built.availableW()).containsExactly(60_000L, 50_000L);
        assertThat(built.problem().stationCapacityW()).containsExactly(60_000L, 50_000L);
        assertThat(built.problem().sessionStation()).containsExactly(0, 1, 0);
        assertThat(built.problem().sessionConnectorMaxW()).containsExactly(60_000L, 22_000L, 60_000L);
        assertThat(built.problem().sessionRequestedW()).containsExactly(20_000L, 11_000L, 30_000L);
        assertThat(built.problem().sessionVehicleMaxW()).containsExactly(50_000L, 11_000L, 50_000L);
    }
}
