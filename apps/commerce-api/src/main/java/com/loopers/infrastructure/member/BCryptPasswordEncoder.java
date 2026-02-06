package com.loopers.infrastructure.member;

import com.loopers.domain.member.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 알고리즘을 사용한 비밀번호 암호화 구현체
 *
 *
 * TDD Green Phase: 테스트를 통과시키는 구현
 */
@Component
public class BCryptPasswordEncoder implements PasswordEncoder {

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder delegate;

    /**
     * 기본 생성자
     * BCrypt strength 10 사용 (기본값)
     */
    public BCryptPasswordEncoder() {
        this.delegate = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    /**
     * BCrypt strength를 지정하는 생성자
     *
     * @param strength 4~31 사이의 값 (높을수록 안전하지만 느림)
     */
    public BCryptPasswordEncoder(int strength) {
        this.delegate = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(strength);
    }

    /**
     * 평문 비밀번호를 BCrypt로 암호화
     *
     * @param rawPassword 평문 비밀번호
     * @return BCrypt로 암호화된 비밀번호 (예: $2a$10$...)
     */
    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    /**
     * 평문 비밀번호와 암호화된 비밀번호가 일치하는지 검증
     *
     * @param rawPassword 평문 비밀번호
     * @param encodedPassword BCrypt로 암호화된 비밀번호
     * @return 일치 여부
     */
    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}
