package com.chargewise.controller;

import com.chargewise.dto.PageResponse;
import com.chargewise.dto.SessionResponse;
import com.chargewise.security.CurrentUser;
import com.chargewise.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/history")
    @Operation(summary = "The authenticated driver's charging sessions, newest first")
    public PageResponse<SessionResponse> history(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sessions.history(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of the authenticated driver's sessions (404 for anyone else's)",
            description = "Energy delivered is simulated with the constant-power model.")
    public SessionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return sessions.get(CurrentUser.id(jwt), id);
    }
}
