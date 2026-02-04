package com.loopers.config;

import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public UserService userService(UserRepository userRepository, PasswordEncryptor passwordEncryptor) {
        return new UserService(userRepository, passwordEncryptor);
    }
}
