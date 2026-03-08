package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    private final PasswordEncoder fakeEncoder = new PasswordEncoder() {
        @Override
        public String encode(String rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded:" + rawPassword);
        }
    };

    @DisplayName("회원 생성 시 각 필드의 검증 로직은 VO에게 위임한다.")
    @Test
    void create_member_success() {
        // given
        MemberId memberId = new MemberId("user1");
        BirthDate birthDate = new BirthDate("1997-01-01");
        Password.validate("Valid123!", birthDate);
        Password password = Password.ofEncoded(fakeEncoder.encode("Valid123!"));
        Name name = new Name("앤드류");
        Email email = new Email("test@test.com");

        // when
        Member member = Member.create(memberId, password, name, email, birthDate);

        // then
        assertThat(member).isNotNull();
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class UpdatePassword {

        @DisplayName("현재 비밀번호가 일치하면 새 비밀번호로 변경된다.")
        @Test
        void updatePassword_success() {
            // given
            String currentRaw = "OldPass123!";
            String newRaw = "NewPass456!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Member member = Member.create(
                    new MemberId("user1"),
                    Password.ofEncoded(fakeEncoder.encode(currentRaw)),
                    new Name("앤드류"),
                    new Email("test@test.com"),
                    birthDate
            );

            // when
            member.updatePassword(currentRaw, newRaw, fakeEncoder);

            // then
            assertThat(member.getPassword().matches(newRaw, fakeEncoder)).isTrue();
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 예외가 발생한다.")
        @Test
        void updatePassword_fail_wrongCurrentPassword() {
            // given
            String currentRaw = "OldPass123!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Member member = Member.create(
                    new MemberId("user1"),
                    Password.ofEncoded(fakeEncoder.encode(currentRaw)),
                    new Name("앤드류"),
                    new Email("test@test.com"),
                    birthDate
            );

            // when & then
            assertThatThrownBy(() -> member.updatePassword("WrongPass1!", "NewPass456!", fakeEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("새 비밀번호가 정책을 위반하면 예외가 발생한다.")
        @Test
        void updatePassword_fail_invalidNewPassword() {
            // given
            String currentRaw = "OldPass123!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Member member = Member.create(
                    new MemberId("user1"),
                    Password.ofEncoded(fakeEncoder.encode(currentRaw)),
                    new Name("앤드류"),
                    new Email("test@test.com"),
                    birthDate
            );

            // when & then - 특수문자 없는 비밀번호
            assertThatThrownBy(() -> member.updatePassword(currentRaw, "NewPass456", fakeEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("새 비밀번호가 기존과 동일하면 예외가 발생한다.")
        @Test
        void updatePassword_fail_samePassword() {
            // given
            String currentRaw = "OldPass123!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Member member = Member.create(
                    new MemberId("user1"),
                    Password.ofEncoded(fakeEncoder.encode(currentRaw)),
                    new Name("앤드류"),
                    new Email("test@test.com"),
                    birthDate
            );

            // when & then
            assertThatThrownBy(() -> member.updatePassword(currentRaw, currentRaw, fakeEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

}
