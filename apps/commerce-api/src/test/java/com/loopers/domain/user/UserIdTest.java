package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserIdTest {

    @DisplayName("UserId를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNull_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> new UserId(null));
        }

        @DisplayName("빈 문자열이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withBlank_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> new UserId("  "));
        }

        @DisplayName("11자 이상이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withLengthOver10_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> new UserId("a1234567890"));
        }

        @DisplayName("특수문자가 포함되면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withSpecialCharacters_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> new UserId("user-id"));
            assertThrows(IllegalArgumentException.class, () -> new UserId("user_id"));
        }

        @DisplayName("영문·숫자 1~10자이면 생성된다.")
        @Test
        void create_withValidAlphanumeric_shouldSucceed() {
            assertThat(new UserId("a").value()).isEqualTo("a");
            assertThat(new UserId("testuser01").value()).isEqualTo("testuser01");
            assertThat(new UserId("ABC123").value()).isEqualTo("ABC123");
        }

        @DisplayName("경계값 10자이면 생성된다.")
        @Test
        void create_with10Chars_shouldSucceed() {
            assertThat(new UserId("1234567890").value()).isEqualTo("1234567890");
        }
    }
}
