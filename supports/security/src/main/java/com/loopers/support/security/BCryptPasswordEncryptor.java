package com.loopers.support.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 알고리즘을 사용한 비밀번호 암호화 구현체
 * <p>
 * Spring Security Crypto의 BCryptPasswordEncoder를 위임받아 사용
 */
@Component
public class BCryptPasswordEncryptor implements PasswordEncryptor {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encryptedPassword) {
        return delegate.matches(rawPassword, encryptedPassword);
    }
}
