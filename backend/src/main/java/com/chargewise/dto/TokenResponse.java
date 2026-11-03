package com.chargewise.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
}
