package com.loopers.domain.member;

public interface PasswordEncoder {
    /**
     * 평문 비밀번호를 암호화
     * @param rawPassword 평문 비밀번호
     * @return 암호화된 비밀번호
     */
    String encode(String rawPassword);

    /**
     * 평문 비밀번호와 암호화된 비밀번호가 일치하는지 확인
     * @param rawPassword 평문 비밀번호
     * @param encodedPassword 암호화된 비밀번호
     * @return 일치 여부
     */
    boolean matches(String rawPassword, String encodedPassword);
}
