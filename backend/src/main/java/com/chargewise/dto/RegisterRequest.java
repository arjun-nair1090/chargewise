package com.chargewise.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public self-registration; it always creates a DRIVER account (D7). BCrypt accepts at most 72 bytes. */
public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    /** Surrounding whitespace is not part of an email address; case is normalised by the service. */
    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
