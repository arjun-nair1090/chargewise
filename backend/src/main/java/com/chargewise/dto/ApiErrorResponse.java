package com.chargewise.dto;

import java.time.Instant;
import java.util.List;

/** The body of every error response (architecture D8). */
public record ApiErrorResponse(int status, String code, String message, List<FieldErrorResponse> fieldErrors,
        Instant timestamp, String path) {
}
