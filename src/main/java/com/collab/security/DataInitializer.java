package com.collab.security;

import com.collab.common.enums.UserRole;
import com.collab.entity.User;
import com.collab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.full-name:Admin}")
    private String adminFullName;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin already exists: {}", adminEmail);
            return;
        }

        User admin = User.builder()
                .fullName(adminFullName)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .currentRole(UserRole.ADMIN)
                .roles(Set.of(UserRole.ADMIN))
                .verified(true)
                .build();

        userRepository.save(admin);
        log.info("Admin created: {}", adminEmail);
    }
}