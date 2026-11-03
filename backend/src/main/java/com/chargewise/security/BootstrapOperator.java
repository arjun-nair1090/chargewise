package com.chargewise.security;

import com.chargewise.entity.Role;
import com.chargewise.entity.User;
import com.chargewise.repository.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial OPERATOR from {@code CHARGEWISE_BOOTSTRAP_OPERATOR_EMAIL/_PASSWORD} when both are set and no
 * account with that email exists. Public registration only ever creates drivers (D7).
 */
@Component
public class BootstrapOperator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapOperator.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String email;
    private final String password;

    public BootstrapOperator(UserRepository users, PasswordEncoder passwordEncoder, Clock clock,
            @Value("${chargewise.bootstrap-operator.email:}") String email,
            @Value("${chargewise.bootstrap-operator.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isBlank() || users.existsByEmail(email)) {
            return;
        }
        users.save(new User("Operator", email, passwordEncoder.encode(password), Role.OPERATOR, clock.instant()));
        log.info("Bootstrap operator account created for {}", email);
    }
}
