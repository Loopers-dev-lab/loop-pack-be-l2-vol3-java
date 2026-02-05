package com.loopers.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberTest {

    private final PasswordEncoder stubEncoder = new PasswordEncoder() {
        @Override
        public String encode(String rawPassword) {
            return "encoded_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    };

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testuser1";
            String password = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            // Act
            Member member = Member.create(
                loginId, password, name, birthDate, email, stubEncoder
            );

            // Assert
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(loginId),
                () -> assertThat(member.getPassword()).isEqualTo("encoded_" + password), // Stub이 반환한 값
                () -> assertThat(member.getName()).isEqualTo(name),
                () -> assertThat(member.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(member.getEmail()).isEqualTo(email)
            );
        }
    }

    @DisplayName("로그인ID 검증 시, ")
    @Nested
    class ValidateLoginId {

        @DisplayName("영문과 숫자 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // Arrange
            String invalidLoginId = "test@user";

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    invalidLoginId, "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 검증 시, ")
    @Nested
    class ValidatePassword {

        @DisplayName("8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // Arrange
            String shortPassword = "Test12!"; // 7자

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", shortPassword, "홍길동",
                    LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // Arrange
            String longPassword = "Test1234!Test1234"; // 17자

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", longPassword, "홍길동",
                    LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("허용되지 않은 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // Arrange
            String invalidPassword = "Test123한글!"; // 한글 포함

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", invalidPassword, "홍길동",
                    LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // Arrange
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String passwordWithBirthDate = "Test19900115!"; // 생년월일 포함

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", passwordWithBirthDate, "홍길동",
                    birthDate, "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름 검증 시, ")
    @Nested
    class ValidateName {

        @DisplayName("한글과 영문이 혼합되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsMixedLanguages() {
            // Arrange
            String mixedName = "Hong길동";

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", mixedName,
                    LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글 이름에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenKoreanNameContainsSpace() {
            // Arrange
            String koreanNameWithSpace = "홍 길동";

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", koreanNameWithSpace,
                    LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문 이름의 연속 공백은 하나로 정규화된다.")
        @Test
        void normalizesConsecutiveSpaces_whenEnglishNameHasMultipleSpaces() {
            // Arrange
            String nameWithConsecutiveSpaces = "John  Doe";

            // Act
            Member member = Member.create(
                "testuser1", "Test1234!", nameWithConsecutiveSpaces,
                LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
            );

            // Assert
            assertThat(member.getName()).isEqualTo("John Doe");
        }
    }

    @DisplayName("이메일 검증 시, ")
    @Nested
    class ValidateEmail {

        @DisplayName("올바르지 않은 형식이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // Arrange
            String invalidEmail = "invalid-email";

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Member.create(
                    "testuser1", "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), invalidEmail, stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 변경 시, ")
    @Nested
    class ChangePassword {

        private Member createMember() {
            return Member.create(
                "testuser1", "Test1234!", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // Arrange
            Member member = createMember();
            String wrongCurrentPassword = "WrongPass1!";
            String newPassword = "NewPass5678!";

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword(wrongCurrentPassword, newPassword, stubEncoder);
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("현재 비밀번호가 일치하지 않습니다.");
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // Arrange
            Member member = createMember();
            String currentPassword = "Test1234!";
            String samePassword = "Test1234!";

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword(currentPassword, samePassword, stubEncoder);
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesRules() {
            // Arrange
            Member member = createMember();
            String currentPassword = "Test1234!";
            String shortPassword = "short"; // 8자 미만

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword(currentPassword, shortPassword, stubEncoder);
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // Arrange
            Member member = createMember();
            String currentPassword = "Test1234!";
            String passwordWithBirthDate = "Pass19900115!"; // 생년월일 포함

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                member.changePassword(currentPassword, passwordWithBirthDate, stubEncoder);
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exception.getMessage()).isEqualTo("비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        @DisplayName("모든 조건이 유효하면, 비밀번호가 정상적으로 변경된다.")
        @Test
        void changesPassword_whenAllConditionsAreValid() {
            // Arrange
            Member member = createMember();
            String currentPassword = "Test1234!";
            String newPassword = "NewPass5678!";

            // Act
            member.changePassword(currentPassword, newPassword, stubEncoder);

            // Assert
            assertThat(member.getPassword()).isEqualTo("encoded_" + newPassword);
        }
    }
}
