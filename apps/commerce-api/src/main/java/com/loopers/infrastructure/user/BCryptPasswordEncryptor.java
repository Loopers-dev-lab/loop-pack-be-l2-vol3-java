package com.loopers.infrastructure.user;

import com.loopers.domain.user.PasswordEncryptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * PasswordEncryptor 어댑터 (Infrastructure Layer)
 *
 * Spring Security의 BCryptPasswordEncoder를 사용하여 비밀번호를 암호화한다.
 */
@Component
public class BCryptPasswordEncryptor implements PasswordEncryptor {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return this.encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return this.encoder.matches(rawPassword, encodedPassword);
    }
}