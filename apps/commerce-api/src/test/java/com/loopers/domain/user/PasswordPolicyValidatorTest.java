package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @DisplayName("비밀번호 길이 검증")
    @Nested
    class LengthValidation {

        @DisplayName("비밀번호가 8자 미만이면 예외가 발생한다")
        @Test
        void throwsException_whenPasswordLessThan8Characters() {
            // arrange
            String password = "Abc123!";  // 7자
            String birthDate = "1990-01-01";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(password, birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과이면 예외가 발생한다")
        @Test
        void throwsException_whenPasswordMoreThan16Characters() {
            // arrange
            String password = "Abcdefgh123456!@#";  // 17자
            String birthDate = "1990-01-01";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(password, birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 이상 16자 이하이면 길이 검증을 통과한다")
        @Test
        void passes_whenPasswordLengthIsValid() {
            // arrange
            String password = "Abcd1234!";  // 9자
            String birthDate = "1990-01-01";

            // act & assert
            assertThatCode(() -> validator.validate(password, birthDate))
                .doesNotThrowAnyException();
        }
    }

    @DisplayName("비밀번호 허용 문자 검증")
    @Nested
    class CharacterValidation {

        @DisplayName("비밀번호에 공백이 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenPasswordContainsSpace() {
            // arrange
            String password = "Abcd 1234!";
            String birthDate = "1990-01-01";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(password, birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 한글이 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenPasswordContainsKorean() {
            // arrange
            String password = "Abcd1234한글!";
            String birthDate = "1990-01-01";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(password, birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 영문 대소문자, 숫자, ASCII 특수문자로만 구성되면 통과한다")
        @Test
        void passes_whenPasswordContainsOnlyAllowedCharacters() {
            // arrange
            String password = "Abc123!@#$";
            String birthDate = "1990-01-01";

            // act & assert
            assertThatCode(() -> validator.validate(password, birthDate))
                .doesNotThrowAnyException();
        }
    }

    @DisplayName("생년월일 포함 검증")
    @Nested
    class BirthDateValidation {

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenPasswordContainsBirthDate() {
            // arrange
            String password = "Abc19900101!";  // 생년월일 19900101 포함
            String birthDate = "1990-01-01";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validate(password, birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되지 않으면 통과한다")
        @Test
        void passes_whenPasswordDoesNotContainBirthDate() {
            // arrange
            String password = "Abc12345!@";
            String birthDate = "1990-01-01";

            // act & assert
            assertThatCode(() -> validator.validate(password, birthDate))
                .doesNotThrowAnyException();
        }
    }

    @DisplayName("비밀번호 변경 시 동일 여부 검증")
    @Nested
    class SamePasswordValidation {

        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면 예외가 발생한다")
        @Test
        void throwsException_whenNewPasswordIsSameAsOldPassword() {
            // arrange
            String oldPassword = "Abcd1234!";
            String newPassword = "Abcd1234!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                validator.validatePasswordChange(oldPassword, newPassword);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 다르면 통과한다")
        @Test
        void passes_whenNewPasswordIsDifferentFromOldPassword() {
            // arrange
            String oldPassword = "Abcd1234!";
            String newPassword = "Efgh5678@";

            // act & assert
            assertThatCode(() -> validator.validatePasswordChange(oldPassword, newPassword))
                .doesNotThrowAnyException();
        }
    }
}
