package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @DisplayName("유효한 이메일 형식으로 Email을 생성할 수 있다")
    @Test
    void create_withValidFormat_succeeds() {
        Email email = new Email("test@example.com");
        assertThat(email.value()).isEqualTo("test@example.com");
    }

    @DisplayName("이메일이 xx@yy.zz 형식에 맞지 않으면 User 객체 생성에 실패한다")
    @Test
    void create_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> new Email("invalid-email"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("@가 없으면 생성에 실패한다")
    @Test
    void create_withoutAtSign_throwsException() {
        assertThatThrownBy(() -> new Email("testexample.com"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("null이면 생성에 실패한다")
    @Test
    void create_withNull_throwsException() {
        assertThatThrownBy(() -> new Email(null))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("도메인 부분이 없으면 생성에 실패한다")
    @Test
    void create_withoutDomain_throwsException() {
        assertThatThrownBy(() -> new Email("test@"))
            .isInstanceOf(CoreException.class);
    }
}
