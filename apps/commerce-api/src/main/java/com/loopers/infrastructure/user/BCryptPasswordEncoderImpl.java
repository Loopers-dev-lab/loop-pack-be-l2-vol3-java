package com.loopers.infrastructure.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.loopers.domain.user.PasswordEncoder;

@Component
public class BCryptPasswordEncoderImpl implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate;

    public BCryptPasswordEncoderImpl() {
        this.delegate = new BCryptPasswordEncoder();
    }

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}
