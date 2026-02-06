package com.loopers.support.validation;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MemberValidatorUtilTest {

    @DisplayName("비밀번호 검증")
    @Nested
    class PasswordValidation {

        @DisplayName("비밀번호 길이 검증")
        @Nested
        class LengthValidation {

            @DisplayName("8자 미만이면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordLessThan8Characters() {
                // arrange
                String password = "Abc123!";  // 7자
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("16자 초과면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordMoreThan16Characters() {
                // arrange
                String password = "Abcdefgh12345678!";  // 17자
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }
        }

        @DisplayName("비밀번호 문자 타입 검증")
        @Nested
        class CharacterTypeValidation {

            @DisplayName("영문이 없으면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordHasNoLetter() {
                // arrange
                String password = "12345678!@";  // 영문 없음
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("숫자가 없으면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordHasNoDigit() {
                // arrange
                String password = "Abcdefgh!@";  // 숫자 없음
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("특수문자가 없으면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordHasNoSpecialCharacter() {
                // arrange
                String password = "Abcdefgh12";  // 특수문자 없음
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }
        }

        @DisplayName("생년월일 포함 검증")
        @Nested
        class BirthDateValidation {

            @DisplayName("생년월일(yyyyMMdd)이 포함되면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordContainsBirthDate() {
                // arrange
                String password = "Abc19900115!";  // 생년월일 포함
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }
        }

        @DisplayName("허용되지 않는 문자 검증")
        @Nested
        class InvalidCharacterValidation {

            @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordContainsKorean() {
                // arrange
                String password = "Abcd1234한글!";
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("공백이 포함되면 BAD_REQUEST 예외가 발생한다")
            @Test
            void throwsException_whenPasswordContainsSpace() {
                // arrange
                String password = "Abcd 1234!";
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertThatThrownBy(() -> MemberValidatorUtil.validatePassword(password, birthDate))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }
        }

        @DisplayName("유효한 비밀번호")
        @Nested
        class ValidPassword {

            @DisplayName("모든 조건을 만족하면 예외가 발생하지 않는다")
            @Test
            void doesNotThrowException_whenPasswordIsValid() {
                // arrange
                String password = "Abcd1234!";  // 8자, 영문+숫자+특수문자, 생년월일 미포함
                LocalDate birthDate = LocalDate.of(1990, 1, 15);

                // act & assert
                assertDoesNotThrow(() -> MemberValidatorUtil.validatePassword(password, birthDate));
            }
        }
    }

    @DisplayName("로그인 ID 검증")
    @Nested
    class LoginIdValidation {

        @DisplayName("영문+숫자만 있으면 예외가 발생하지 않는다")
        @Test
        void doesNotThrowException_whenLoginIdIsValid() {
            // arrange
            String loginId = "testuser123";

            // act & assert
            assertDoesNotThrow(() -> MemberValidatorUtil.validateLoginId(loginId));
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenLoginIdIsEmpty() {
            // arrange
            String loginId = "";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateLoginId(loginId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("특수문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenLoginIdContainsSpecialCharacter() {
            // arrange
            String loginId = "test@user";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateLoginId(loginId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenLoginIdContainsKorean() {
            // arrange
            String loginId = "test유저";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateLoginId(loginId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("이메일 검증")
    @Nested
    class EmailValidation {

        @DisplayName("올바른 이메일 포맷이면 예외가 발생하지 않는다")
        @Test
        void doesNotThrowException_whenEmailIsValid() {
            // arrange
            String email = "test@example.com";

            // act & assert
            assertDoesNotThrow(() -> MemberValidatorUtil.validateEmail(email));
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenEmailIsEmpty() {
            // arrange
            String email = "";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateEmail(email))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("@가 없으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenEmailHasNoAtSign() {
            // arrange
            String email = "testexample.com";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateEmail(email))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("도메인이 없으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenEmailHasNoDomain() {
            // arrange
            String email = "test@";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateEmail(email))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("이름 검증")
    @Nested
    class NameValidation {

        @DisplayName("이름이 있으면 예외가 발생하지 않는다")
        @Test
        void doesNotThrowException_whenNameIsValid() {
            // arrange
            String name = "홍길동";

            // act & assert
            assertDoesNotThrow(() -> MemberValidatorUtil.validateName(name));
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNameIsEmpty() {
            // arrange
            String name = "";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateName(name))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("공백만 있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNameIsBlank() {
            // arrange
            String name = "   ";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateName(name))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("생년월일 검증")
    @Nested
    class BirthDateValidation {

        @DisplayName("유효한 생년월일이면 예외가 발생하지 않는다")
        @Test
        void doesNotThrowException_whenBirthDateIsValid() {
            // arrange
            LocalDate birthDate = LocalDate.of(1990, 1, 15);

            // act & assert
            assertDoesNotThrow(() -> MemberValidatorUtil.validateBirthDate(birthDate));
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenBirthDateIsNull() {
            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateBirthDate(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("미래 날짜면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenBirthDateIsInFuture() {
            // arrange
            LocalDate birthDate = LocalDate.now().plusDays(1);

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validateBirthDate(birthDate))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("비밀번호 변경 검증")
    @Nested
    class PasswordChangeValidation {

        @DisplayName("새 비밀번호가 기존 비밀번호와 다르면 예외가 발생하지 않는다")
        @Test
        void doesNotThrowException_whenNewPasswordIsDifferent() {
            // arrange
            String currentPassword = "OldPass123!";
            String newPassword = "NewPass456!";

            // act & assert
            assertDoesNotThrow(() -> MemberValidatorUtil.validatePasswordChange(currentPassword, newPassword));
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String currentPassword = "SamePass123!";
            String newPassword = "SamePass123!";

            // act & assert
            assertThatThrownBy(() -> MemberValidatorUtil.validatePasswordChange(currentPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
