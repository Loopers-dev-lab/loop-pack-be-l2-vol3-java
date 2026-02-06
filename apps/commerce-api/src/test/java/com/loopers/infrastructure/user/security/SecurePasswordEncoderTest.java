package com.loopers.infrastructure.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurePasswordEncoderTest {

    @DisplayName("비밀번호를 인코딩하고 검증한다.")
    @Test
    void encodesAndMatchesPassword() {
        // arrange
        SecurePasswordEncoder securePasswordEncoder = new SecurePasswordEncoder();

        // act
        String passwordHash = securePasswordEncoder.encode("secret");

        // assert
        assertAll(
                () -> assertThat(securePasswordEncoder.matches("secret", passwordHash)).isTrue(),
                () -> assertThat(securePasswordEncoder.matches("wrong", passwordHash)).isFalse()
        );
    }
}
