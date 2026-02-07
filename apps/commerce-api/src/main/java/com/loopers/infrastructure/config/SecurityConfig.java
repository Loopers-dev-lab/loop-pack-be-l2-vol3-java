package com.loopers.infrastructure.config;

import com.loopers.domain.member.PasswordHasher;
import com.loopers.infrastructure.security.BCryptPasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }
}
