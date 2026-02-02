package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BirthDateValidatorTest {

    private final BirthDateValidator validator = new BirthDateValidator();

    @DisplayName("생년월일 형식 검증")
    @Nested
    class FormatValidation {

        @DisplayName("생년월일이 yyyy-MM-dd 형식이면 통과한다")
        @Test
        void passes_whenBirthDateIsValidFormat() {
            // arrange
            String birthDate = "1990-01-15";

            // act & assert
            assertThatCode(() -> validator.validate(birthDate))
                .doesNotThrowAnyException();
        }

        @DisplayName("생년월일이 dd-MM-yyyy 형식이면 예외가 발생한다")
        @Test
        void throwsException_whenBirthDateIsDdMmYyyyFormat() {
            // arrange
            String birthDate = "15-01-1990";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일에 슬래시를 사용하면 예외가 발생한다")
        @Test
        void throwsException_whenBirthDateUsesSlashes() {
            // arrange
            String birthDate = "1990/01/15";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 빈 문자열이면 예외가 발생한다")
        @Test
        void throwsException_whenBirthDateIsEmpty() {
            // arrange
            String birthDate = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 날짜이면 예외가 발생한다")
        @Test
        void throwsException_whenBirthDateIsInvalidDate() {
            // arrange
            String birthDate = "1990-13-45";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
