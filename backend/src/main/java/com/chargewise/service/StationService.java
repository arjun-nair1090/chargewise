package com.chargewise.service;

import com.chargewise.dto.ConnectorRequest;
import com.chargewise.dto.ConnectorResponse;
import com.chargewise.dto.PageResponse;
import com.chargewise.dto.StationDtos.AvailabilityResponse;
import com.chargewise.dto.StationDtos.NearbyStationResponse;
import com.chargewise.dto.StationDtos.SlotAvailability;
import com.chargewise.dto.StationRequest;
import com.chargewise.dto.StationResponse;
import com.chargewise.entity.Booking;
import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.BookingRepository;
import com.chargewise.repository.ConnectorRepository;
import com.chargewise.repository.StationRepository;
import com.chargewise.repository.StationRepository.NearbyRow;
import com.chargewise.service.allocation.AllocationLock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationService {

    static final int MAX_AVAILABILITY_SLOTS = 96;
    private static final int MAX_NEARBY_RESULTS = 50;

    private final StationRepository stations;
    private final ConnectorRepository connectors;
    private final BookingRepository bookings;
    private final StationChangeListener changeListener;
    private final AllocationLock allocationLock;
    private final Clock clock;

    public StationService(StationRepository stations, ConnectorRepository connectors, BookingRepository bookings,
            StationChangeListener changeListener, AllocationLock allocationLock, Clock clock) {
        this.stations = stations;
        this.connectors = connectors;
        this.bookings = bookings;
        this.changeListener = changeListener;
        this.allocationLock = allocationLock;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<StationResponse> list(StationStatus status, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by("id"));
        Page<Station> result = status == null ? stations.findAll(request) : stations.findAllByStatus(status, request);
        Map<Long, List<ConnectorResponse>> byStation = connectorsByStation(
                result.getContent().stream().map(Station::getId).toList());
        return PageResponse.from(result.map(s -> StationResponse.from(s, byStation.getOrDefault(s.getId(), List.of()))));
    }

    /**
     * Every station with its connectors, for the network map and directory. The network is a few thousand sites,
     * so one response (gzip-compressed on the wire) is cheaper for the client than paging through it.
     */
    @Transactional(readOnly = true)
    public List<StationResponse> listAll() {
        List<Station> all = stations.findAll(Sort.by("id"));
        Map<Long, List<ConnectorResponse>> byStation = connectorsByStation(all.stream().map(Station::getId).toList());
        return all.stream().map(s -> StationResponse.from(s, byStation.getOrDefault(s.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public StationResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<NearbyStationResponse> nearby(double lat, double lng, double radiusKm) {
        List<NearbyRow> rows = stations.findNearby(lat, lng, radiusKm, MAX_NEARBY_RESULTS);
        List<Long> ids = rows.stream().map(NearbyRow::getId).toList();
        Map<Long, Station> byId = stations.findAllById(ids).stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));
        Map<Long, List<ConnectorResponse>> connectorMap = connectorsByStation(ids);
        return rows.stream()
                .map(row -> new NearbyStationResponse(
                        StationResponse.from(byId.get(row.getId()), connectorMap.getOrDefault(row.getId(), List.of())),
                        BigDecimal.valueOf(row.getDistanceKm()).setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long stationId, Instant from, Instant to) {
        Station station = find(stationId);
        if (!TimeSlots.isAligned(from)) {
            throw ApiException.validation("from", "must be on a 15-minute boundary");
        }
        if (!TimeSlots.isAligned(to)) {
            throw ApiException.validation("to", "must be on a 15-minute boundary");
        }
        if (!to.isAfter(from)) {
            throw ApiException.validation("to", "must be after from");
        }
        int slotCount = TimeSlots.slotsBetween(from, to);
        if (slotCount > MAX_AVAILABILITY_SLOTS) {
            throw ApiException.validation("to", "window must be at most " + MAX_AVAILABILITY_SLOTS + " slots (24 h)");
        }

        List<Connector> all = connectors.findAllByStationIdOrderByIdAsc(stationId);
        List<Connector> usable = station.getStatus() != StationStatus.OPERATIONAL ? List.of()
                : all.stream().filter(c -> c.getStatus() == ConnectorStatus.OPERATIONAL).toList();
        Map<Long, List<Booking>> assignedByConnector = bookings.findAssignedOverlapping(List.of(stationId), from, to)
                .stream().collect(Collectors.groupingBy(b -> b.getConnector().getId()));

        List<SlotAvailability> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            Instant start = from.plus(TimeSlots.SLOT.multipliedBy(i));
            Instant end = start.plus(TimeSlots.SLOT);
            Map<ConnectorType, Integer> freeByType = new EnumMap<>(ConnectorType.class);
            all.forEach(c -> freeByType.put(c.getConnectorType(), 0));
            int free = 0;
            for (Connector connector : usable) {
                boolean occupied = assignedByConnector.getOrDefault(connector.getId(), List.of()).stream()
                        .anyMatch(b -> TimeSlots.overlaps(b.getRequestedStart(), b.getRequestedEnd(), start, end));
                if (!occupied) {
                    free++;
                    freeByType.merge(connector.getConnectorType(), 1, Integer::sum);
                }
            }
            slots.add(new SlotAvailability(start, end, free, freeByType));
        }
        return new AvailabilityResponse(stationId, from, to, TimeSlots.SLOT_MINUTES, usable.size(), slots);
    }

    @Transactional
    public StationResponse create(StationRequest request) {
        Station station = stations.save(new Station(request.name().trim(), request.address().trim(),
                blankToNull(request.city()), blankToNull(request.state()), request.latitude(), request.longitude(),
                request.gridCapacityKw(), request.status(), clock.instant()));
        return toResponse(station);
    }

    @Transactional
    public StationResponse update(Long id, StationRequest request) {
        allocationLock.acquire(); // a grid cut or outage must not interleave with an allocation run (D5)
        Station station = find(id);
        StationStatus previousStatus = station.getStatus();
        BigDecimal previousGridKw = station.getGridCapacityKw();
        station.update(request.name().trim(), request.address().trim(), blankToNull(request.city()),
                blankToNull(request.state()), request.latitude(), request.longitude(), request.gridCapacityKw(),
                request.status());
        changeListener.stationChanged(station, previousStatus, previousGridKw);
        return toResponse(station);
    }

    @Transactional
    public ConnectorResponse addConnector(Long stationId, ConnectorRequest request) {
        Station station = find(stationId);
        return ConnectorResponse.from(connectors.save(
                new Connector(station, request.connectorType(), request.maxPowerKw(), request.status())));
    }

    @Transactional
    public ConnectorResponse setConnectorStatus(Long connectorId, ConnectorStatus status) {
        allocationLock.acquire();
        Connector connector = connectors.findById(connectorId).orElseThrow(() -> ApiException.notFound("Connector"));
        ConnectorStatus previous = connector.getStatus();
        connector.update(connector.getConnectorType(), connector.getMaxPowerKw(), status);
        changeListener.connectorChanged(connector, previous);
        return ConnectorResponse.from(connector);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Station find(Long id) {
        return stations.findById(id).orElseThrow(() -> ApiException.notFound("Station"));
    }

    private StationResponse toResponse(Station station) {
        return StationResponse.from(station, connectors.findAllByStationIdOrderByIdAsc(station.getId()).stream()
                .map(ConnectorResponse::from).toList());
    }

    private Map<Long, List<ConnectorResponse>> connectorsByStation(List<Long> stationIds) {
        if (stationIds.isEmpty()) {
            return Map.of();
        }
        return connectors.findAllByStationIdInOrderByIdAsc(stationIds).stream()
                .map(ConnectorResponse::from)
                .collect(Collectors.groupingBy(ConnectorResponse::stationId));
    }
}
