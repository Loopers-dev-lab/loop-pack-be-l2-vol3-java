package com.loopers.domain.user.policy;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);

    private PasswordPolicy passwordPolicy;

    @BeforeEach
    void setUp() {
        passwordPolicy = new PasswordPolicy();
    }

    @DisplayName("회원가입 비밀번호 검증 시,")
    @Nested
    class ValidateForSignup {

        @DisplayName("유효한 비밀번호면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordIsValid() {
            // arrange
            String validPassword = "Test1234!";

            // act & assert
            assertThatCode(() -> passwordPolicy.validateForSignup(validPassword, BIRTH_DATE))
                    .doesNotThrowAnyException();
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            String shortPassword = "Short1!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForSignup(shortPassword, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("8~16자");
        }

        @DisplayName("비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            String longPassword = "ThisPasswordIs17!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForSignup(longPassword, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("8~16자");
        }

        @DisplayName("비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForSignup(null, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("8~16자");
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            String passwordWithBirthDate = "Pass19900115!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForSignup(passwordWithBirthDate, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("생년월일");
        }

        @DisplayName("비밀번호에 허용되지 않는 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // arrange
            String passwordWithKorean = "Test1234한글";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForSignup(passwordWithKorean, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("영문 대소문자, 숫자, 특수문자만");
        }
    }
}