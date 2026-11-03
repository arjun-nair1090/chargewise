package com.chargewise.controller;

import com.chargewise.dto.lab.MatchingLabRequest;
import com.chargewise.dto.lab.MatchingLabResponse;
import com.chargewise.dto.lab.MaxFlowLabRequest;
import com.chargewise.dto.lab.MaxFlowLabResponse;
import com.chargewise.dto.lab.QuickselectLabRequest;
import com.chargewise.dto.lab.QuickselectLabResponse;
import com.chargewise.dto.lab.SegmentTreeLabRequest;
import com.chargewise.dto.lab.SegmentTreeLabResponse;
import com.chargewise.service.AlgorithmLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/algorithms")
@Tag(name = "Algorithm Lab", description = "Each endpoint runs the algorithm and its baseline on the same input. "
        + "Limits: 5,000 vertices, 50,000 edges, 1,000,000 values (413 beyond them).")
public class AlgorithmLabController {

    private final AlgorithmLabService lab;

    public AlgorithmLabController(AlgorithmLabService lab) {
        this.lab = lab;
    }

    @PostMapping("/matching/execute")
    @Operation(summary = "Maximum bipartite matching vs greedy first-available",
            description = "Matching maximises the number of request–connector assignments in the given graph. "
                    + "It does not minimise waiting time or guarantee fairness.")
    public MatchingLabResponse matching(@Valid @RequestBody MatchingLabRequest request) {
        return lab.matching(request);
    }

    @PostMapping("/max-flow/execute")
    @Operation(summary = "Edmonds-Karp on a flow network, or on a power network against the greedy allocator",
            description = "Edmonds-Karp maximises the total feasible flow (power) in the modeled network. It does "
                    + "not maximise fully served vehicles, minimise waiting time or guarantee fairness.")
    public MaxFlowLabResponse maxFlow(@Valid @RequestBody MaxFlowLabRequest request) {
        return lab.maxFlow(request);
    }

    @PostMapping("/quickselect/execute")
    @Operation(summary = "Randomized Quickselect vs full sorting for order statistics or percentiles")
    public QuickselectLabResponse quickselect(@Valid @RequestBody QuickselectLabRequest request) {
        return lab.quickselect(request);
    }

    @PostMapping("/segment-tree/query")
    @Operation(summary = "Segment tree vs naive range scan over the same operation sequence")
    public SegmentTreeLabResponse segmentTree(@Valid @RequestBody SegmentTreeLabRequest request) {
        return lab.segmentTree(request);
    }
}
