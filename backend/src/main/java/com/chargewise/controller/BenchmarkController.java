package com.chargewise.controller;

import com.chargewise.dto.benchmark.BenchmarkRequest;
import com.chargewise.dto.benchmark.BenchmarkRunResponse;
import com.chargewise.security.CurrentUser;
import com.chargewise.service.BenchmarkService;
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
@RequestMapping("/api/v1/benchmarks")
@Tag(name = "Operator: benchmarks")
public class BenchmarkController {

    private final BenchmarkService benchmarks;

    public BenchmarkController(BenchmarkService benchmarks) {
        this.benchmarks = benchmarks;
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Queue a benchmark run",
            description = "Runs asynchronously on a single benchmark thread, so runs never overlap; poll "
                    + "GET /benchmarks/{id} until the status is COMPLETED or FAILED. Inputs derive from the seed only. "
                    + "Each algorithm and its baseline are verified correct on the identical input before timing.")
    public BenchmarkRunResponse run(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BenchmarkRequest request) {
        return benchmarks.start(request, CurrentUser.id(jwt));
    }

    @GetMapping
    @Operation(summary = "The 20 most recent benchmark runs, without their reports")
    public List<BenchmarkRunResponse> list() {
        return benchmarks.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One benchmark run with its measured report once completed",
            description = "Times are nanoseconds: median, quartiles (nearest rank), min and max over the trials, "
                    + "with each side's work counter and the bytes it allocated in one extra run.")
    public BenchmarkRunResponse get(@PathVariable Long id) {
        return benchmarks.get(id);
    }
}
