package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginIdTest {

    @DisplayName("유효한 영문 및 숫자 조합으로 LoginId를 생성할 수 있다")
    @Test
    void create_withValidFormat_succeeds() {
        LoginId loginId = new LoginId("user1234");
        assertThat(loginId.value()).isEqualTo("user1234");
    }

    @DisplayName("ID가 영문 및 숫자 10자 이내 형식에 맞지 않으면 User 객체 생성에 실패한다")
    @Test
    void create_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> new LoginId("한글아이디"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("10자를 초과하면 생성에 실패한다")
    @Test
    void create_withTooLong_throwsException() {
        assertThatThrownBy(() -> new LoginId("abcdefghijk"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("특수문자가 포함되면 생성에 실패한다")
    @Test
    void create_withSpecialChars_throwsException() {
        assertThatThrownBy(() -> new LoginId("user!@#"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("null이면 생성에 실패한다")
    @Test
    void create_withNull_throwsException() {
        assertThatThrownBy(() -> new LoginId(null))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("빈 문자열이면 생성에 실패한다")
    @Test
    void create_withEmpty_throwsException() {
        assertThatThrownBy(() -> new LoginId(""))
            .isInstanceOf(CoreException.class);
    }
}
