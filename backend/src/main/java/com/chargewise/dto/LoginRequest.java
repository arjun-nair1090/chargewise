package com.chargewise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password) {

    /** Surrounding whitespace is not part of an email address; case is normalised by the service. */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
