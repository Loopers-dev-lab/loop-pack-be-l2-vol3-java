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

    @DisplayName("비밀번호 변경 검증 시,")
    @Nested
    class ValidateForChange {

        @DisplayName("유효한 새 비밀번호면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenNewPasswordIsValid() {
            // arrange
            String currentPassword = "OldPass123!";
            String newPassword = "NewPass456!";

            // act & assert
            assertThatCode(() -> passwordPolicy.validateForChange(currentPassword, newPassword, BIRTH_DATE))
                    .doesNotThrowAnyException();
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String samePassword = "SamePass123!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForChange(samePassword, samePassword, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("현재 비밀번호와 달라야");
        }

        @DisplayName("현재 비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsNull() {
            // arrange
            String newPassword = "NewPass456!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForChange(null, newPassword, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("현재 비밀번호는 비어있을 수 없습니다");
        }

        @DisplayName("현재 비밀번호가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsBlank() {
            // arrange
            String newPassword = "NewPass456!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForChange("   ", newPassword, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("현재 비밀번호는 비어있을 수 없습니다");
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            String currentPassword = "OldPass123!";
            String newPasswordWithBirthDate = "Pass19900115!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForChange(currentPassword, newPasswordWithBirthDate, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("생년월일");
        }

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsTooShort() {
            // arrange
            String currentPassword = "OldPass123!";
            String shortPassword = "Short1!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    passwordPolicy.validateForChange(currentPassword, shortPassword, BIRTH_DATE)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).contains("8~16자");
        }
    }
}