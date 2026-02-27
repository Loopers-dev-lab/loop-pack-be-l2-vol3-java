package com.loopers.domain.member;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.PasswordEncryptor;
import com.loopers.domain.member.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MemberTest {

    private static final PasswordEncryptor STUB_ENCRYPTOR = new PasswordEncryptor() {
        @Override
        public String encode(String rawPassword) { return "encoded_" + rawPassword; }
        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    };

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class SignUp {

        @DisplayName("유효한 Command가 주어지면, VO가 내부에서 생성되어 정상적으로 생성된다.")
        @Test
        void createsMember_whenCommandIsValid() {
            // arrange
            MemberCommand.SignUp command = new MemberCommand.SignUp(
                "testuser", "Password123!", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            );

            // act
            Member member = Member.signUp(command, STUB_ENCRYPTOR);

            // assert
            assertAll(
                () -> assertThat(member.getLoginId().value()).isEqualTo("testuser"),
                () -> assertThat(member.getPassword().value()).isEqualTo("encoded_Password123!"),
                () -> assertThat(member.getName().value()).isEqualTo("홍길동"),
                () -> assertThat(member.getBirthDate().value()).isEqualTo(LocalDate.of(1990, 1, 15)),
                () -> assertThat(member.getEmail().value()).isEqualTo("test@example.com")
            );
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 새 비밀번호로 변경하면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenNewPasswordIsValid() {
            // arrange
            MemberCommand.SignUp command = new MemberCommand.SignUp(
                "testuser", "Password123!", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            );
            Member member = Member.signUp(command, STUB_ENCRYPTOR);
            Password newPassword = new Password("newEncodedPassword");

            // act
            member.changePassword(newPassword);

            // assert
            assertThat(member.getPassword()).isEqualTo(newPassword);
        }
    }

    @DisplayName("DB에서 복원할 때, ")
    @Nested
    class Reconstruct {

        @DisplayName("id를 포함한 모든 필드가 올바르게 복원된다.")
        @Test
        void reconstructsMember_withAllFieldsIncludingId() {
            // act
            Member member = Member.reconstruct(1L, "testuser", "encodedPassword123", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            // assert
            assertAll(
                () -> assertThat(member.getId()).isEqualTo(1L),
                () -> assertThat(member.getLoginId().value()).isEqualTo("testuser"),
                () -> assertThat(member.getPassword().value()).isEqualTo("encodedPassword123"),
                () -> assertThat(member.getName().value()).isEqualTo("홍길동"),
                () -> assertThat(member.getBirthDate().value()).isEqualTo(LocalDate.of(1990, 1, 15)),
                () -> assertThat(member.getEmail().value()).isEqualTo("test@example.com")
            );
        }
    }
}
