package com.example.auth.config;

import com.example.auth.model.AuthUser;
import com.example.auth.model.Role;
import com.example.auth.repo.AuthUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthSeedConfig {

    @Bean
    CommandLineRunner authSeedRunner(
            AuthUserRepository authUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.enabled:true}") boolean seedEnabled,
            @Value("${app.seed.admin.email:admin@example.com}") String adminEmail,
            @Value("${app.seed.admin.password:admin123}") String adminPassword,
            @Value("${app.seed.user.email:user@example.com}") String userEmail,
            @Value("${app.seed.user.password:user123}") String userPassword
    ) {
        return args -> {
            if (!seedEnabled) {
                return;
            }

            String normalizedAdminEmail = adminEmail.toLowerCase().trim();
            if (authUserRepository.findByEmail(normalizedAdminEmail).isEmpty()) {
                AuthUser admin = new AuthUser();
                admin.setEmail(normalizedAdminEmail);
                admin.setName("System Admin");
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                admin.setRole(Role.ROLE_ADMIN);
                admin.setActive(true);
                authUserRepository.save(admin);
            }

            String normalizedUserEmail = userEmail.toLowerCase().trim();
            if (authUserRepository.findByEmail(normalizedUserEmail).isEmpty()) {
                AuthUser user = new AuthUser();
                user.setEmail(normalizedUserEmail);
                user.setName("Seed User");
                user.setPasswordHash(passwordEncoder.encode(userPassword));
                user.setRole(Role.ROLE_USER);
                user.setActive(true);
                authUserRepository.save(user);
            }
        };
    }
}
