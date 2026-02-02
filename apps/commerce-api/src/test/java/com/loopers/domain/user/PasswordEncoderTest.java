package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordEncoderTest {

    @DisplayName("비밀번호를 암호화할 때,")
    @Nested
    class Encode {

        @DisplayName("평문과 다른 암호화된 값을 반환한다.")
        @Test
        void returnsEncodedValue_differentFromRawPassword() {
            // arrange
            PasswordEncoder encoder = new FakePasswordEncoder();
            String rawPassword = "password123!";

            // act
            String encoded = encoder.encode(rawPassword);

            // assert
            assertThat(encoded).isNotEqualTo(rawPassword);
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    class Matches {

        @DisplayName("평문과 암호화된 값이 일치하면 true를 반환한다.")
        @Test
        void returnsTrue_whenPasswordMatches() {
            // arrange
            PasswordEncoder encoder = new FakePasswordEncoder();
            String rawPassword = "password123!";
            String encoded = encoder.encode(rawPassword);

            // act
            boolean result = encoder.matches(rawPassword, encoded);

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("평문과 암호화된 값이 일치하지 않으면 false를 반환한다.")
        @Test
        void returnsFalse_whenPasswordDoesNotMatch() {
            // arrange
            PasswordEncoder encoder = new FakePasswordEncoder();
            String encoded = encoder.encode("password123!");

            // act
            boolean result = encoder.matches("wrongPassword!", encoded);

            // assert
            assertThat(result).isFalse();
        }
    }
}
