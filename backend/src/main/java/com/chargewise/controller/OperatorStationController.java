package com.chargewise.controller;

import com.chargewise.dto.ConnectorRequest;
import com.chargewise.dto.ConnectorResponse;
import com.chargewise.dto.StationDtos.ConnectorStatusRequest;
import com.chargewise.dto.StationRequest;
import com.chargewise.dto.StationResponse;
import com.chargewise.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operator")
@Tag(name = "Operator")
public class OperatorStationController {

    private final StationService stations;

    public OperatorStationController(StationService stations) {
        this.stations = stations;
    }

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a station")
    public StationResponse createStation(@Valid @RequestBody StationRequest request) {
        return stations.create(request);
    }

    @PutMapping("/stations/{id}")
    @Operation(summary = "Update a station",
            description = "Taking a station offline or into maintenance waitlists its future assignments and "
                    + "interrupts active sessions; lowering grid capacity re-allocates power (architecture D5).")
    public StationResponse updateStation(@PathVariable Long id, @Valid @RequestBody StationRequest request) {
        return stations.update(id, request);
    }

    @PostMapping("/stations/{id}/connectors")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a connector to a station")
    public ConnectorResponse addConnector(@PathVariable Long id, @Valid @RequestBody ConnectorRequest request) {
        return stations.addConnector(id, request);
    }

    @PatchMapping("/connectors/{id}/status")
    @Operation(summary = "Change a connector's status",
            description = "OUT_OF_SERVICE waitlists the connector's future assignments (architecture D5).")
    public ConnectorResponse setConnectorStatus(@PathVariable Long id,
            @Valid @RequestBody ConnectorStatusRequest request) {
        return stations.setConnectorStatus(id, request.status());
    }
}
