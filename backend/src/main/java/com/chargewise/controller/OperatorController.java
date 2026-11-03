package com.chargewise.controller;

import com.chargewise.dto.BookingResponse;
import com.chargewise.dto.DashboardResponse;
import com.chargewise.dto.PageResponse;
import com.chargewise.dto.SessionResponse;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.SessionStatus;
import com.chargewise.service.BookingService;
import com.chargewise.service.DashboardService;
import com.chargewise.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operator")
@Tag(name = "Operator: monitoring")
public class OperatorController {

    private final DashboardService dashboard;
    private final SessionService sessions;
    private final BookingService bookings;

    public OperatorController(DashboardService dashboard, SessionService sessions, BookingService bookings) {
        this.dashboard = dashboard;
        this.sessions = sessions;
        this.bookings = bookings;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Counts by status, current utilisation and live waiting-time percentiles",
            description = "Waiting time is assigned_at − created_at for assigned bookings plus the age of every "
                    + "queued request; percentiles use randomized Quickselect. Energy and sessions are simulated.")
    public DashboardResponse dashboard() {
        return dashboard.dashboard();
    }

    @GetMapping("/sessions")
    @Operation(summary = "All charging sessions, newest first, optionally filtered by status")
    public PageResponse<SessionResponse> sessions(@RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sessions.listAll(status, page, size);
    }

    @GetMapping("/requests")
    @Operation(summary = "All charging requests, newest first, optionally filtered by status")
    public PageResponse<BookingResponse> requests(@RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return bookings.listAll(status, page, size);
    }
}
