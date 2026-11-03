package com.chargewise.dto;

import com.chargewise.entity.Role;
import com.chargewise.entity.User;
import java.time.Instant;

/** Public view of an account; the password hash is never exposed. */
public record UserResponse(Long id, String name, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
