package com.chargewise.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated user's id is the token subject. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
