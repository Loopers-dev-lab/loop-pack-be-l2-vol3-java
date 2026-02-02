package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class LoginIdTest {

    @DisplayName("LoginId를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("영문과 숫자로만 이루어져 있으면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenAlphanumeric() {
            // arrange
            String value = "user123";

            // act & assert
            assertThatCode(() -> new LoginId(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsSpecialCharacter() {
            // arrange
            String value = "user@123";

            // act & assert
            assertThatThrownBy(() -> new LoginId(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsKorean() {
            // arrange
            String value = "user한글";

            // act & assert
            assertThatThrownBy(() -> new LoginId(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsSpace() {
            // arrange
            String value = "user 123";

            // act & assert
            assertThatThrownBy(() -> new LoginId(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
