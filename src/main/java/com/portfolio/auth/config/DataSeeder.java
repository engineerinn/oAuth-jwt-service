package com.portfolio.auth.config;

import com.portfolio.auth.entity.Role;
import com.portfolio.auth.entity.User;
import com.portfolio.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo convenience only: seeds an ADMIN account so reviewers can exercise the RBAC endpoint
 * immediately. Disable via app.seed-admin=false. Never ship default credentials to production.
 */
@Component
@ConditionalOnProperty(name = "app.seed-admin", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("Admin@12345"))
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());
            log.info("Seeded demo admin account: admin / Admin@12345");
        }
    }
}
