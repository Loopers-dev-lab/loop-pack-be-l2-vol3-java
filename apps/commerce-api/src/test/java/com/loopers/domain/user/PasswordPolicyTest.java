package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1995, 3, 15);

    @DisplayName("비밀번호를 검증할 때, ")
    @Nested
    class Validate {

        @DisplayName("올바른 비밀번호는 검증을 통과한다.")
        @Test
        void passesValidation_whenPasswordIsValid() {
            // act & assert
            assertDoesNotThrow(() -> {
                PasswordPolicy.validate("Abcd1234!", VALID_BIRTH_DATE);
            });
        }

        @DisplayName("비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate(null, VALID_BIRTH_DATE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("Abc123!", VALID_BIRTH_DATE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("Abcdefgh12345678!", VALID_BIRTH_DATE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자(공백)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsWhitespace() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("Abcd 1234!", VALID_BIRTH_DATE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsKorean() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("Abcd가나다1!", VALID_BIRTH_DATE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            LocalDate birthDate = LocalDate.of(1995, 3, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("Ab19950315!", birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
