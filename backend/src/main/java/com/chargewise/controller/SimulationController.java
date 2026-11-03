package com.chargewise.controller;

import com.chargewise.dto.simulation.SimulationComparisonResponse;
import com.chargewise.dto.simulation.SimulationRequest;
import com.chargewise.dto.simulation.SimulationResponse;
import com.chargewise.dto.simulation.SimulationResultResponse;
import com.chargewise.dto.simulation.SimulationRunResponse;
import com.chargewise.security.CurrentUser;
import com.chargewise.service.SimulationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
@Tag(name = "Operator: simulations")
public class SimulationController {

    private final SimulationService simulations;

    public SimulationController(SimulationService simulations) {
        this.simulations = simulations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Store a seeded scenario",
            description = "Shape fields left out take the scenario's preset. The configuration and seed are stored, "
                    + "so the same input can be generated again.")
    public SimulationResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SimulationRequest request) {
        return simulations.create(request, CurrentUser.id(jwt));
    }

    @GetMapping
    @Operation(summary = "The 50 most recent simulations")
    public List<SimulationResponse> list() {
        return simulations.list();
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Run the scenario with the algorithms and with the greedy baselines",
            description = "Both runs use the identical generated input, identified by its SHA-256. A re-run replaces "
                    + "the stored results and reports whether every non-timing metric was reproduced.")
    public SimulationRunResponse run(@PathVariable Long id) {
        return simulations.run(id);
    }

    @GetMapping("/{id}/results")
    @Operation(summary = "The stored result of each strategy")
    public List<SimulationResultResponse> results(@PathVariable Long id) {
        return simulations.results(id);
    }

    @GetMapping("/{id}/comparison")
    @Operation(summary = "Algorithm beside baseline on the identical input",
            description = "Totals over the horizon are measured outcomes; the per-slot evidence fields record whether "
                    + "the baseline ever did better on the identical slot input.")
    public SimulationComparisonResponse comparison(@PathVariable Long id) {
        return simulations.comparison(id);
    }
}
