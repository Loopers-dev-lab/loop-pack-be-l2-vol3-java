package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class PasswordTest {

    @DisplayName("Password를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("8자 이상 16자 이하이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenLengthIsValid() {
            // arrange
            String value = "Passw1!a";

            // act & assert
            assertThatCode(() -> new Password(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenTooShort() {
            // arrange
            String value = "Pass1!a";

            // act & assert
            assertThatThrownBy(() -> new Password(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenTooLong() {
            // arrange
            String value = "Password1!abcdefg";

            // act & assert
            assertThatThrownBy(() -> new Password(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsKorean() {
            // arrange
            String value = "Pass한글1!";

            // act & assert
            assertThatThrownBy(() -> new Password(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsSpace() {
            // arrange
            String value = "Pass 1!ab";

            // act & assert
            assertThatThrownBy(() -> new Password(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("contains를 호출할 때,")
    @Nested
    class Contains {

        @DisplayName("포함된 문자열이면 true를 반환한다.")
        @Test
        void returnsTrue_whenContains() {
            // arrange
            Password password = new Password("Pass19900101!");

            // act & assert
            assertThat(password.contains("19900101")).isTrue();
        }

        @DisplayName("포함되지 않은 문자열이면 false를 반환한다.")
        @Test
        void returnsFalse_whenNotContains() {
            // arrange
            Password password = new Password("Password1!");

            // act & assert
            assertThat(password.contains("19900101")).isFalse();
        }
    }
}