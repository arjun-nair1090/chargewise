package com.chargewise.service;

import com.chargewise.dto.LoginRequest;
import com.chargewise.dto.RegisterRequest;
import com.chargewise.dto.TokenResponse;
import com.chargewise.dto.UserResponse;
import com.chargewise.entity.Role;
import com.chargewise.entity.User;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.UserRepository;
import com.chargewise.security.TokenService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;
    // Compared against when the email is unknown, so both failure paths cost one BCrypt check.
    private final String timingEqualizerHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.timingEqualizerHash = passwordEncoder.encode("timing-equalizer-not-a-password");
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw ApiException.validation("password", "must be at most 72 bytes");
        }
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        try {
            User user = users.saveAndFlush(new User(request.name().trim(), email,
                    passwordEncoder.encode(request.password()), Role.DRIVER, clock.instant()));
            return UserResponse.from(user);
        } catch (DataIntegrityViolationException raced) {
            throw ApiException.conflict("An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(timingEqualizerHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        return tokens.issue(user.get());
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("This account no longer exists"));
    }
}
