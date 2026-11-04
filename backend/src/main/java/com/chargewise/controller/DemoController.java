package com.chargewise.controller;

import com.chargewise.service.demo.DemoDataSeeder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operator/demo")
@Tag(name = "Operator")
public class DemoController {

    private final DemoDataSeeder seeder;

    public DemoController(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    @PostMapping("/reset")
    @Operation(summary = "Replace operational data with the demo dataset",
            description = "Deletes stations, connectors, vehicles, bookings, sessions and allocation runs, then "
                    + "loads datasets/*.json. Registered accounts, simulations and benchmarks are kept.")
    public DemoDataSeeder.SeedSummary reset() {
        return seeder.reset();
    }
}
