package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailValidatorTest {

    private final EmailValidator validator = new EmailValidator();

    @DisplayName("이메일 형식 검증")
    @Nested
    class FormatValidation {

        @DisplayName("이메일이 xx@yy.zz 형식이면 통과한다")
        @Test
        void passes_whenEmailIsValidFormat() {
            // arrange
            String email = "test@example.com";

            // act & assert
            assertThatCode(() -> validator.validate(email))
                .doesNotThrowAnyException();
        }

        @DisplayName("이메일에 @가 없으면 예외가 발생한다")
        @Test
        void throwsException_whenEmailHasNoAtSymbol() {
            // arrange
            String email = "testexample.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 도메인에 .이 없으면 예외가 발생한다")
        @Test
        void throwsException_whenEmailDomainHasNoDot() {
            // arrange
            String email = "test@examplecom";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 빈 문자열이면 예외가 발생한다")
        @Test
        void throwsException_whenEmailIsEmpty() {
            // arrange
            String email = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 @ 앞부분이 비어있으면 예외가 발생한다")
        @Test
        void throwsException_whenEmailLocalPartIsEmpty() {
            // arrange
            String email = "@example.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
