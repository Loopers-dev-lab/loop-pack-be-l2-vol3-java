package com.loopers.infrastructure.security;

import com.loopers.domain.service.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256PasswordEncoderTest {

    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Sha256PasswordEncoder();
    }

    @Test
    @DisplayName("비밀번호 암호화 성공")
    void encrypt_success() {
        // given
        String rawPassword = "password123!";

        // when
        String encoded = encoder.encrypt(rawPassword);

        // then
        assertThat(encoded).isNotNull();
        assertThat(encoded).contains(":");
        assertThat(encoded).isNotEqualTo(rawPassword);
    }

    @Test
    @DisplayName("같은 비밀번호도 매번 다른 해시 생성 (salt)")
    void encrypt_different_hash_each_time() {
        // given
        String rawPassword = "password123!";

        // when
        String encoded1 = encoder.encrypt(rawPassword);
        String encoded2 = encoder.encrypt(rawPassword);

        // then
        assertThat(encoded1).isNotEqualTo(encoded2);
    }

    @Test
    @DisplayName("비밀번호 매칭 성공")
    void matches_success() {
        // given
        String rawPassword = "password123!";
        String encoded = encoder.encrypt(rawPassword);

        // when
        boolean result = encoder.matches(rawPassword, encoded);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("틀린 비밀번호 매칭 실패")
    void matches_fail_wrong_password() {
        // given
        String encoded = encoder.encrypt("password123!");
        String wrongPassword = "wrongPassword!";

        // when
        boolean result = encoder.matches(wrongPassword, encoded);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("잘못된 형식의 인코딩된 비밀번호")
    void matches_fail_invalid_format() {
        // given
        String rawPassword = "password";
        String invalidEncoded = "invalid_format";

        // when
        boolean result = encoder.matches(rawPassword, invalidEncoded);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("빈 salt 형식 처리")
    void matches_fail_empty_salt() {
        // given
        String rawPassword = "password";
        String invalidEncoded = ":hash";

        // when
        boolean result = encoder.matches(rawPassword, invalidEncoded);

        // then
        assertThat(result).isFalse();
    }
}
