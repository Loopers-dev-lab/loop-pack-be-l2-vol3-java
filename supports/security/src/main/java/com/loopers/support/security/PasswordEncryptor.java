package com.loopers.support.security;

/**
 * 비밀번호 암호화를 담당하는 인터페이스
 * <p>
 * 도메인 계층이 특정 암호화 라이브러리에 의존하지 않도록 추상화
 */
public interface PasswordEncryptor {

    /**
     * 평문 비밀번호를 암호화합니다.
     *
     * @param rawPassword 평문 비밀번호
     * @return 암호화된 비밀번호
     */
    String encode(String rawPassword);

    /**
     * 평문 비밀번호와 암호화된 비밀번호가 일치하는지 검증합니다.
     *
     * @param rawPassword 평문 비밀번호
     * @param encryptedPassword 암호화된 비밀번호
     * @return 일치 여부
     */
    boolean matches(String rawPassword, String encryptedPassword);
}
