package com.chargewise.simulation;

import com.chargewise.algorithm.power.FeederTopology;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * One generated scenario: the network, the request stream and the events, in integer watts and watt-hours.
 * {@link #canonicalText()} is a stable line-per-record serialisation and {@link #inputHash()} its SHA-256, so a re-run
 * can prove it used the identical input (architecture D9).
 *
 * @param feeders the D12 feeder layer, or {@code null} for the spec §6.2 network
 */
public record ScenarioInput(
        SimulationConfig config,
        long seed,
        List<SimStation> stations,
        List<SimConnector> connectors,
        List<SimRequest> requests,
        List<Outage> connectorOutages,
        List<Outage> stationOutages,
        List<GridReduction> gridReductions,
        FeederTopology feeders) {

    /** Format tag of {@link #canonicalText()}; bump it whenever the serialisation changes. */
    public static final String CANONICAL_VERSION = "chargewise-scenario v1";

    public record SimStation(int index, long gridCapacityW) {
    }

    /** {@code type} is a {@link ConnectorTypes} index. */
    public record SimConnector(int index, int station, int type, long maxW) {
    }

    /**
     * A driver's request: it may start in any slot from {@code arrivalSlot} to {@code latestStartSlot} inclusive.
     * {@code connectorTypeMask} has bit {@code t} set when the vehicle accepts connector type {@code t}.
     */
    public record SimRequest(int index, int arrivalSlot, int latestStartSlot, int station, int connectorTypeMask,
            long energyWh, long vehicleMaxW) {

        public boolean accepts(int connectorType) {
            return (connectorTypeMask & (1 << connectorType)) != 0;
        }
    }

    /** {@code target} (a connector or a station index) is out of service for slots {@code [fromSlot, toSlot)}. */
    public record Outage(int target, int fromSlot, int toSlot) {

        boolean covers(int slot) {
            return slot >= fromSlot && slot < toSlot;
        }
    }

    /** Every station imports at most {@code percent}% of its grid limit during slots {@code [fromSlot, toSlot)}. */
    public record GridReduction(int fromSlot, int toSlot, int percent) {
    }

    public ScenarioInput {
        stations = List.copyOf(stations);
        connectors = List.copyOf(connectors);
        requests = List.copyOf(requests);
        connectorOutages = List.copyOf(connectorOutages);
        stationOutages = List.copyOf(stationOutages);
        gridReductions = List.copyOf(gridReductions);
    }

    public boolean stationOperational(int station, int slot) {
        return stationOutages.stream().noneMatch(outage -> outage.target() == station && outage.covers(slot));
    }

    /** A connector is usable when it is in service and its station is operational. */
    public boolean connectorOperational(int connector, int slot) {
        return stationOperational(connectors.get(connector).station(), slot)
                && connectorOutages.stream().noneMatch(outage -> outage.target() == connector && outage.covers(slot));
    }

    /** Share of each station's grid limit available in {@code slot}, in percent. */
    public int gridFactorPercent(int slot) {
        int percent = 100;
        for (GridReduction reduction : gridReductions) {
            if (slot >= reduction.fromSlot() && slot < reduction.toSlot()) {
                percent = Math.min(percent, reduction.percent());
            }
        }
        return percent;
    }

    /** One line per record, in index order, with every value the executor reads. */
    public String canonicalText() {
        StringBuilder text = new StringBuilder(CANONICAL_VERSION).append('\n');
        text.append("config scenario=").append(config.scenario())
                .append(" stations=").append(config.stations())
                .append(" connectorsPerStation=").append(config.connectorsPerStation())
                .append(" requests=").append(config.requests())
                .append(" horizonSlots=").append(config.horizonSlots())
                .append(" maxWaitSlots=").append(config.maxWaitSlots())
                .append(" seed=").append(seed).append('\n');
        for (SimStation station : stations) {
            text.append("station ").append(station.index()).append(' ').append(station.gridCapacityW()).append('\n');
        }
        for (SimConnector connector : connectors) {
            text.append("connector ").append(connector.index()).append(' ').append(connector.station()).append(' ')
                    .append(ConnectorTypes.name(connector.type())).append(' ').append(connector.maxW()).append('\n');
        }
        if (feeders != null) {
            long[] capacity = feeders.feederCapacityW();
            for (int f = 0; f < capacity.length; f++) {
                text.append("feeder ").append(f).append(' ').append(capacity[f]).append('\n');
            }
            int[] linkFeeder = feeders.linkFeeder();
            int[] linkStation = feeders.linkStation();
            for (int l = 0; l < linkFeeder.length; l++) {
                text.append("link ").append(l).append(' ').append(linkFeeder[l]).append(' ').append(linkStation[l]).append('\n');
            }
        }
        for (SimRequest request : requests) {
            text.append("request ").append(request.index()).append(' ').append(request.arrivalSlot()).append(' ')
                    .append(request.latestStartSlot()).append(' ').append(request.station()).append(' ')
                    .append(request.connectorTypeMask()).append(' ').append(request.energyWh()).append(' ')
                    .append(request.vehicleMaxW()).append('\n');
        }
        for (Outage outage : connectorOutages) {
            text.append("connectorOutage ").append(outage.target()).append(' ').append(outage.fromSlot()).append(' ')
                    .append(outage.toSlot()).append('\n');
        }
        for (Outage outage : stationOutages) {
            text.append("stationOutage ").append(outage.target()).append(' ').append(outage.fromSlot()).append(' ')
                    .append(outage.toSlot()).append('\n');
        }
        for (GridReduction reduction : gridReductions) {
            text.append("grid ").append(reduction.fromSlot()).append(' ').append(reduction.toSlot()).append(' ')
                    .append(reduction.percent()).append('\n');
        }
        return text.toString();
    }

    /** Lower-case hex SHA-256 of {@link #canonicalText()} in UTF-8. */
    public String inputHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalText().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
