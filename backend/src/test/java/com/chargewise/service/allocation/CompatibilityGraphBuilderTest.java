package com.chargewise.service.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargewise.algorithm.matching.BipartiteMatcher;
import com.chargewise.algorithm.matching.MatchingResult;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.StatusReason;
import com.chargewise.service.allocation.ConnectorState.Window;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompatibilityGraphBuilderTest {

    private static final Instant T = Instant.parse("2026-11-05T10:00:00Z");
    private static final long STATION = 1;

    /** 20 kWh in one hour on a 50 kW CCS2 vehicle. */
    private static BookingCandidate request(long id) {
        return new BookingCandidate(id, STATION, Set.of(ConnectorType.CCS2), 50_000, 20_000, T,
                T.plus(Duration.ofHours(1)));
    }

    private static ConnectorState connector(long id, ConnectorType type, long maxW, boolean stationUp, boolean up,
            Window... assigned) {
        return new ConnectorState(id, STATION, type, maxW, stationUp, up, List.of(assigned));
    }

    private static ConnectorState healthy(long id) {
        return connector(id, ConnectorType.CCS2, 60_000, true, true);
    }

    private static StatusReason onlyReason(ConnectorState... connectors) {
        CompatibilityGraph result = CompatibilityGraphBuilder.build(List.of(request(1)), List.of(connectors));
        assertThat(result.graph().neighbors(0)).isEmpty();
        return result.reasons().get(0);
    }

    @Test
    void compatibleConnectorAtTheNamedStationGetsAnEdge() {
        ConnectorState elsewhere = new ConnectorState(9, 2, ConnectorType.CCS2, 60_000, true, true, List.of());
        CompatibilityGraph result = CompatibilityGraphBuilder.build(List.of(request(1)),
                List.of(elsewhere, healthy(10)));

        assertThat(result.graph().neighbors(0)).containsExactly(1);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void wrongConnectorTypeIsNoCompatibleConnectorType() {
        assertThat(onlyReason(connector(10, ConnectorType.CHADEMO, 60_000, true, true)))
                .isEqualTo(StatusReason.NO_COMPATIBLE_CONNECTOR_TYPE);
    }

    @Test
    void stationWithoutConnectorsIsNoCompatibleConnectorType() {
        assertThat(onlyReason()).isEqualTo(StatusReason.NO_COMPATIBLE_CONNECTOR_TYPE);
    }

    @Test
    void connectorTooWeakForTheEnergyInTheWindowIsInsufficientConnectorPower() {
        // 20 kWh in 60 min needs 20 kW; a 19.99 kW connector cannot deliver it.
        assertThat(onlyReason(connector(10, ConnectorType.CCS2, 19_990, true, true)))
                .isEqualTo(StatusReason.INSUFFICIENT_CONNECTOR_POWER);
        CompatibilityGraph exact = CompatibilityGraphBuilder.build(List.of(request(1)),
                List.of(connector(10, ConnectorType.CCS2, 20_000, true, true)));
        assertThat(exact.graph().neighbors(0)).containsExactly(0);
    }

    @Test
    void vehicleMaximumAlsoLimitsDeliverablePower() {
        BookingCandidate slowCar = new BookingCandidate(1, STATION, Set.of(ConnectorType.CCS2), 10_000, 20_000, T,
                T.plus(Duration.ofHours(1)));
        CompatibilityGraph result = CompatibilityGraphBuilder.build(List.of(slowCar), List.of(healthy(10)));
        assertThat(result.reasons()).containsEntry(0, StatusReason.INSUFFICIENT_CONNECTOR_POWER);
    }

    @Test
    void offlineStationIsStationNotOperational() {
        assertThat(onlyReason(connector(10, ConnectorType.CCS2, 60_000, false, true)))
                .isEqualTo(StatusReason.STATION_NOT_OPERATIONAL);
    }

    @Test
    void outOfServiceConnectorsAreConnectorsOutOfService() {
        assertThat(onlyReason(connector(10, ConnectorType.CCS2, 60_000, true, false)))
                .isEqualTo(StatusReason.CONNECTORS_OUT_OF_SERVICE);
    }

    @Test
    void overlappingAssignmentIsConnectorsBookedInWindow() {
        Window overlapping = new Window(T.plus(Duration.ofMinutes(45)), T.plus(Duration.ofHours(2)));
        assertThat(onlyReason(connector(10, ConnectorType.CCS2, 60_000, true, true, overlapping)))
                .isEqualTo(StatusReason.CONNECTORS_BOOKED_IN_WINDOW);
    }

    @Test
    void backToBackAssignmentLeavesTheConnectorFree() {
        Window before = new Window(T.minus(Duration.ofHours(1)), T);
        Window after = new Window(T.plus(Duration.ofHours(1)), T.plus(Duration.ofHours(2)));
        CompatibilityGraph result = CompatibilityGraphBuilder.build(List.of(request(1)),
                List.of(connector(10, ConnectorType.CCS2, 60_000, true, true, before, after)));
        assertThat(result.graph().neighbors(0)).containsExactly(0);
    }

    @Test
    void structuralRulesAreReportedBeforeTemporaryOnes() {
        // Offline, out of service and the wrong type: the request can never be served here, so REJECTED wins.
        assertThat(onlyReason(connector(10, ConnectorType.CHADEMO, 60_000, false, false)))
                .isEqualTo(StatusReason.NO_COMPATIBLE_CONNECTOR_TYPE);
        // Right type but too weak, at an offline station: still structural.
        assertThat(onlyReason(connector(10, ConnectorType.CCS2, 5_000, false, false)))
                .isEqualTo(StatusReason.INSUFFICIENT_CONNECTOR_POWER);
        // Offline station with an out-of-service connector: the station is reported first.
        assertThat(onlyReason(connector(10, ConnectorType.CCS2, 60_000, false, false)))
                .isEqualTo(StatusReason.STATION_NOT_OPERATIONAL);
    }

    @Test
    void rulesFilterTheConnectorSetInOrder() {
        // One connector fails on type, the other only because it is booked: the request reaches the last rule.
        Window overlapping = new Window(T, T.plus(Duration.ofHours(1)));
        assertThat(onlyReason(connector(10, ConnectorType.TYPE2_AC, 60_000, true, true),
                connector(11, ConnectorType.CCS2, 60_000, true, true, overlapping)))
                .isEqualTo(StatusReason.CONNECTORS_BOOKED_IN_WINDOW);
    }

    @Test
    void unmatchedRequestThatHadEdgesLostContention() {
        CompatibilityGraph result = CompatibilityGraphBuilder.build(List.of(request(1), request(2)),
                List.of(healthy(10)));
        MatchingResult matching = new BipartiteMatcher().match(result.graph());

        assertThat(matching.cardinality()).isEqualTo(1);
        assertThat(result.reasonFor(0, matching)).isNull();
        assertThat(result.reasonFor(1, matching)).isEqualTo(StatusReason.LOST_CONTENTION);
    }

    @Test
    void unmatchedRequestWithoutEdgesKeepsItsFilterReason() {
        CompatibilityGraph result = CompatibilityGraphBuilder.build(List.of(request(1)),
                List.of(connector(10, ConnectorType.CCS2, 60_000, true, false)));
        MatchingResult matching = new BipartiteMatcher().match(result.graph());
        assertThat(result.reasonFor(0, matching)).isEqualTo(StatusReason.CONNECTORS_OUT_OF_SERVICE);
    }
}
