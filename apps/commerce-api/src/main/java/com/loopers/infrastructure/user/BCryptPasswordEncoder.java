package com.loopers.infrastructure.user;

import com.loopers.domain.user.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 사용자(User) 도메인의 {@link PasswordEncoder} 인터페이스를 BCrypt 알고리즘으로 구현한 클래스.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 인터페이스를 인프라스트럭처 계층에서 구현하며,
 * 내부적으로 Spring Security의 BCryptPasswordEncoder에 위임한다.</p>
 */
@Component("userPasswordEncoder")
public class BCryptPasswordEncoder implements PasswordEncoder {

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder delegate =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    /**
     * 평문 비밀번호를 BCrypt 알고리즘으로 암호화한다.
     *
     * @param rawPassword 평문 비밀번호
     * @return BCrypt로 암호화된 비밀번호
     */
    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    /**
     * 평문 비밀번호와 암호화된 비밀번호의 일치 여부를 확인한다.
     *
     * @param rawPassword     평문 비밀번호
     * @param encodedPassword BCrypt로 암호화된 비밀번호
     * @return 일치 여부
     */
    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}
