package com.chargewise.controller;

import com.chargewise.dto.VehicleRequest;
import com.chargewise.dto.VehicleResponse;
import com.chargewise.security.CurrentUser;
import com.chargewise.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles")
public class VehicleController {

    private final VehicleService vehicles;

    public VehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a vehicle for the authenticated driver")
    public VehicleResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VehicleRequest request) {
        return vehicles.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @Operation(summary = "The authenticated driver's vehicles")
    public List<VehicleResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return vehicles.list(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of the authenticated driver's vehicles (404 for anyone else's)")
    public VehicleResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return vehicles.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update one of the authenticated driver's vehicles")
    public VehicleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody VehicleRequest request) {
        return vehicles.update(CurrentUser.id(jwt), id, request);
    }
}
