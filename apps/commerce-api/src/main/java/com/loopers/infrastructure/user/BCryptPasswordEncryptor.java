package com.loopers.infrastructure.user;

import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * PasswordEncryptor 어댑터 (Infrastructure Layer)
 *
 * Spring Security의 BCryptPasswordEncoder를 사용하여 비밀번호를 암호화한다.
 * BCryptPasswordEncoder는 null 입력 시 IllegalArgumentException을 발생시키므로,
 * 도메인 예외(CoreException)로 일관된 에러 응답을 보장하기 위해 null을 선검증한다.
 */
@Component
public class BCryptPasswordEncryptor implements PasswordEncryptor {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD, "암호화할 비밀번호는 필수입니다.");
        }
        return this.encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD, "비밀번호는 필수입니다.");
        }
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD, "암호화된 비밀번호가 존재하지 않습니다.");
        }
        return this.encoder.matches(rawPassword, encodedPassword);
    }
}