package com.loopers.domain.user;

/**
 * 비밀번호 암호화 인터페이스.
 */
public interface PasswordEncoder {

    /**
     * 평문 비밀번호를 암호화한다.
     */
    String encode(String rawPassword);

    /**
     * 평문 비밀번호와 암호화된 비밀번호가 일치하는지 검증한다.
     */
    boolean matches(String rawPassword, String encodedPassword);
}
