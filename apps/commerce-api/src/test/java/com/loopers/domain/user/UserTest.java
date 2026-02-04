package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @DisplayName("이름 마스킹")
    @Nested
    class GetMaskedName {

        @DisplayName("이름의 마지막 글자가 *으로 반환된다.")
        @Test
        void returnsNameWithLastCharMasked() {
            // Arrange
            User user = new User("testuser", "encrypted", "홍길동", "19900101", "test@example.com");

            // Act
            String maskedName = user.getMaskedName();

            // Assert
            assertThat(maskedName).isEqualTo("홍길*");
        }

        @DisplayName("한 글자 이름이면 *으로 반환된다.")
        @Test
        void returnsStar_whenNameIsSingleChar() {
            // Arrange
            User user = new User("testuser", "encrypted", "김", "19900101", "test@example.com");

            // Act
            String maskedName = user.getMaskedName();

            // Assert
            assertThat(maskedName).isEqualTo("*");
        }
    }
}
