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
}
