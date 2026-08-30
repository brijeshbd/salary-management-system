package com.acme.salary.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds exactly one HR Manager account, unconditionally (unlike {@link
 * com.acme.salary.seed.DataSeeder}, this isn't profile-gated) - without it nothing could ever log
 * in. Idempotent: skips if any HrUser already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrUserSeeder implements ApplicationRunner {

    private final HrUserRepository hrUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.hr-admin.email}")
    private String email;

    @Value("${app.hr-admin.password}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (hrUserRepository.count() > 0) {
            return;
        }

        HrUser user = HrUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(HrRole.HR_MANAGER)
                .build();
        hrUserRepository.save(user);
        log.info("Seeded HR user: {}", email);
    }
}
