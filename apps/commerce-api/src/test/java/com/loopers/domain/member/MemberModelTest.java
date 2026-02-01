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

class MemberModelTest {

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
            MemberModel member = MemberModel.create(
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
                MemberModel.create(
                    invalidLoginId, "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), "test@example.com", stubEncoder
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
