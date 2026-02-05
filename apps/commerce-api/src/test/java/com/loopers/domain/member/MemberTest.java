package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    @DisplayName("유효한 정보로 Member를 생성할 수 있다")
    @Test
    void create_withValidInfo_succeeds() {
        Member member = new Member(
            new LoginId("user1"),
            new Password("encodedPw"),
            "홍길동",
            BirthDate.from("1990-01-15"),
            new Email("test@example.com"),
            Gender.MALE
        );

        assertThat(member.getLoginId().value()).isEqualTo("user1");
        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getGender()).isEqualTo(Gender.MALE);
        assertThat(member.getPoint()).isEqualTo(0L);
    }

    @DisplayName("이름이 null이면 생성에 실패한다")
    @Test
    void create_withNullName_throwsException() {
        assertThatThrownBy(() -> new Member(
            new LoginId("user1"),
            new Password("encodedPw"),
            null,
            BirthDate.from("1990-01-15"),
            new Email("test@example.com"),
            Gender.MALE
        )).isInstanceOf(CoreException.class);
    }

    @DisplayName("이름이 빈 문자열이면 생성에 실패한다")
    @Test
    void create_withBlankName_throwsException() {
        assertThatThrownBy(() -> new Member(
            new LoginId("user1"),
            new Password("encodedPw"),
            "  ",
            BirthDate.from("1990-01-15"),
            new Email("test@example.com"),
            Gender.MALE
        )).isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호를 변경할 수 있다")
    @Test
    void changePassword_updatesPassword() {
        Member member = new Member(
            new LoginId("user1"),
            new Password("oldEncodedPw"),
            "홍길동",
            BirthDate.from("1990-01-15"),
            new Email("test@example.com"),
            Gender.MALE
        );

        member.changePassword(new Password("newEncodedPw"));
        assertThat(member.getPassword().encoded()).isEqualTo("newEncodedPw");
    }
}
