package com.loopers.infrastructure.user.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.loopers.domain.user.PasswordEncoder;

@Component
public class SecurePasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Override
    public String encode(String password) {
        return bCryptPasswordEncoder.encode(password);
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        return bCryptPasswordEncoder.matches(password, passwordHash);
    }
}
