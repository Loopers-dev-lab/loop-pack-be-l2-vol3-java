package com.loopers.domain.user;

/**
 * 비밀번호 암호화 포트 (Domain Layer)
 *
 * Domain이 인프라(Spring Security)에 의존하지 않도록 추상화한 인터페이스.
 * 실제 구현은 Infrastructure 계층의 어댑터가 담당한다.
 */
public interface PasswordEncryptor {

    /**
     * 평문 비밀번호를 암호화한다.
     *
     * @param rawPassword 평문 비밀번호
     * @return 암호화된 비밀번호
     */
    String encode(String rawPassword);

    /**
     * 평문 비밀번호와 암호화된 비밀번호가 일치하는지 확인한다.
     *
     * @param rawPassword 평문 비밀번호
     * @param encodedPassword 암호화된 비밀번호
     * @return 일치 여부
     */
    boolean matches(String rawPassword, String encodedPassword);
}