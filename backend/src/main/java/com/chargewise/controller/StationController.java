package com.chargewise.controller;

import com.chargewise.dto.PageResponse;
import com.chargewise.dto.StationDtos.AvailabilityResponse;
import com.chargewise.dto.StationDtos.NearbyStationResponse;
import com.chargewise.dto.StationResponse;
import com.chargewise.entity.StationStatus;
import com.chargewise.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stations")
@Tag(name = "Stations")
public class StationController {

    private final StationService stations;

    public StationController(StationService stations) {
        this.stations = stations;
    }

    @GetMapping
    @Operation(summary = "List stations with their connectors")
    public PageResponse<StationResponse> list(
            @RequestParam(required = false) StationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return stations.list(status, page, size);
    }

    @GetMapping("/nearby")
    @Operation(summary = "Stations within a radius, nearest first (great-circle distance)")
    public List<NearbyStationResponse> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @RequestParam(defaultValue = "10") @DecimalMin(value = "0", inclusive = false) @DecimalMax("100")
            double radiusKm) {
        return stations.nearby(lat, lng, radiusKm);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One station with its connectors")
    public StationResponse get(@PathVariable Long id) {
        return stations.get(id);
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Free connectors per 15-minute slot, derived from assigned bookings",
            description = "from/to are ISO-8601 instants on 15-minute boundaries; at most 96 slots.")
    public AvailabilityResponse availability(@PathVariable Long id, @RequestParam Instant from,
            @RequestParam Instant to) {
        return stations.availability(id, from, to);
    }
}
