package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MemberModelTest {

    private static final LoginId LOGIN_ID = new LoginId("testuser");
    private static final Password ENCODED_PASSWORD = new Password("encodedPassword123");
    private static final MemberName NAME = new MemberName("홍길동");
    private static final BirthDate BIRTH_DATE = new BirthDate(LocalDate.of(1990, 1, 15));
    private static final Email EMAIL = new Email("test@example.com");

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class SignUp {

        @DisplayName("유효한 VO가 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenAllFieldsAreValid() {
            // act
            MemberModel member = MemberModel.signUp(LOGIN_ID, ENCODED_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // assert
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(member.getPassword()).isEqualTo(ENCODED_PASSWORD),
                () -> assertThat(member.getName()).isEqualTo(NAME),
                () -> assertThat(member.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(member.getEmail()).isEqualTo(EMAIL)
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
            MemberModel member = MemberModel.signUp(LOGIN_ID, ENCODED_PASSWORD, NAME, BIRTH_DATE, EMAIL);
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
            MemberModel member = MemberModel.reconstruct(1L, "testuser", "encodedPassword123", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

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
