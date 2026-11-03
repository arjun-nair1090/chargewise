package com.chargewise.controller;

import com.chargewise.dto.BookingRequest;
import com.chargewise.dto.BookingResponse;
import com.chargewise.dto.PageResponse;
import com.chargewise.security.CurrentUser;
import com.chargewise.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a charging request (PENDING until the next allocation run)",
            description = "Window: ISO-8601 with offset, 15-minute boundaries, 1–48 slots, starting in the future "
                    + "and at most 14 days ahead. Energy must fit the battery headroom and the vehicle's maximum "
                    + "charging power over the window.")
    public BookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest request) {
        return bookings.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @Operation(summary = "The authenticated driver's bookings, newest first")
    public PageResponse<BookingResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return bookings.list(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of the authenticated driver's bookings (404 for anyone else's)")
    public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookings.get(CurrentUser.id(jwt), id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending, waitlisted or assigned booking before its window starts")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookings.cancel(CurrentUser.id(jwt), id);
    }
}
