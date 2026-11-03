package com.chargewise.controller;

import com.chargewise.dto.AllocationRunRequest;
import com.chargewise.dto.AllocationRunResponse;
import com.chargewise.security.CurrentUser;
import com.chargewise.service.allocation.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/operator/allocation")
@Tag(name = "Operator: allocation")
public class AllocationController {

    private final AllocationService allocation;

    public AllocationController(AllocationService allocation) {
        this.allocation = allocation;
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run the allocation pipeline over pending and waitlisted requests",
            description = "Bipartite matching assigns connectors, then Edmonds-Karp allocates station power. "
                    + "Matching maximises the number of request–connector assignments in the modeled "
                    + "compatibility graph. Edmonds-Karp maximises the total feasible power in the modeled "
                    + "network. Neither minimises waiting time or guarantees fairness. The interval defaults to "
                    + "the current slot plus 15 days.")
    public AllocationRunResponse run(@AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) AllocationRunRequest request) {
        return allocation.run(request == null ? new AllocationRunRequest(null, null) : request, CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "A stored allocation run with its full result")
    public AllocationRunResponse get(@PathVariable Long id) {
        return allocation.get(id);
    }
}
