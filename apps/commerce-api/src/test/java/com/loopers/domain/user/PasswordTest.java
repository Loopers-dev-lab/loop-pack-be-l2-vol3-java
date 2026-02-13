package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_BIRTHDAY = "1994-05-25";

    @DisplayName("비밀번호를 검증할 때, ")
    @Nested
    class Validate {

        @DisplayName("정상적인 비밀번호면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrowException_whenValid() {
            // act & assert
            assertDoesNotThrow(() -> Password.validate(VALID_PASSWORD, VALID_BIRTHDAY));
        }

        @DisplayName("비밀번호가 비어있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Password.validate("", VALID_BIRTHDAY);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordIsShorterThan8() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Password.validate("qwer@12", VALID_BIRTHDAY);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과이면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordIsLongerThan16() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Password.validate("qwer@123412341234", VALID_BIRTHDAY);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않은 문자가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordContainsInvalidCharacters() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Password.validate("qwer@1234한글", VALID_BIRTHDAY);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(대시 포함)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordContainsBirthdayWithDash() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Password.validate("qwer@1994-05-25", VALID_BIRTHDAY);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(대시 미포함)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordContainsBirthdayWithoutDash() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Password.validate("qwer19940525", VALID_BIRTHDAY);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
