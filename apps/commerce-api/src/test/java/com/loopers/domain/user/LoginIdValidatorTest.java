package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdValidatorTest {

    private final LoginIdValidator validator = new LoginIdValidator();

    @DisplayName("loginId 형식 검증")
    @Nested
    class FormatValidation {

        @DisplayName("loginId가 영문과 숫자로만 구성되면 통과한다")
        @Test
        void passes_whenLoginIdContainsOnlyAlphanumeric() {
            // arrange
            String loginId = "user123";

            // act & assert
            assertThatCode(() -> validator.validate(loginId))
                .doesNotThrowAnyException();
        }

        @DisplayName("loginId에 특수문자가 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenLoginIdContainsSpecialCharacters() {
            // arrange
            String loginId = "user@123";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(loginId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId에 한글이 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenLoginIdContainsKorean() {
            // arrange
            String loginId = "user한글";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(loginId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId에 공백이 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenLoginIdContainsSpace() {
            // arrange
            String loginId = "user 123";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(loginId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId가 빈 문자열이면 예외가 발생한다")
        @Test
        void throwsException_whenLoginIdIsEmpty() {
            // arrange
            String loginId = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(loginId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
