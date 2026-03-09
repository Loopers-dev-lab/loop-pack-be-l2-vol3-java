package com.loopers.domain.user;

/**
 * 비밀번호 암호화 인터페이스.
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의하며, 인프라스트럭처 계층에서 구현한다.
 */
public interface PasswordEncoder {
    /**
     * 평문 비밀번호를 암호화(해시)한다.
     *
     * @param rawPassword 평문 비밀번호
     * @return 암호화된 비밀번호
     */
    String encode(String rawPassword);

    /**
     * 평문 비밀번호와 암호화된 비밀번호의 일치 여부를 확인한다.
     *
     * @param rawPassword     평문 비밀번호
     * @param encodedPassword 암호화된 비밀번호
     * @return 일치하면 true
     */
    boolean matches(String rawPassword, String encodedPassword);
}
