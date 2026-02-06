package com.loopers.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    @DisplayName("유효한 이메일 생성 성공")
    void create_success() {
        // given
        String value = "test@example.com";

        // when
        Email email = Email.of(value);

        // then
        assertThat(email.getValue()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("이메일 null이면 예외")
    void create_fail_null() {
        // given
        String value = null;

        // when and then
        assertThatThrownBy(() -> Email.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일은 필수 입력값입니다.");
    }

    @Test
    @DisplayName("이메일 공백이면 예외")
    void create_fail_blank() {
        // given
        String value = "   ";

        // when and then
        assertThatThrownBy(() -> Email.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일은 필수 입력값입니다.");
    }

    @Test
    @DisplayName("이메일 형식 오류 - @ 없음")
    void create_fail_no_at() {
        // given
        String value = "testexample.com";

        // when and then
        assertThatThrownBy(() -> Email.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일 형식");
    }

    @Test
    @DisplayName("이메일 형식 오류 - 도메인 없음")
    void create_fail_no_domain() {
        // given
        String value = "test@";

        // when and then
        assertThatThrownBy(() -> Email.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일 형식");
    }

    @Test
    @DisplayName("이메일 공백 trim 처리")
    void create_success_with_trim() {
        // given
        String value = "  test@example.com  ";

        // when
        Email email = Email.of(value);

        // then
        assertThat(email.getValue()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("동일한 이메일은 equals/hashCode 동등")
    void equals_hashCode_consistency() {
        Email email1 = Email.of("test@example.com");
        Email email2 = Email.of("test@example.com");

        assertThat(email1).isEqualTo(email2);
        assertThat(email1.hashCode()).isEqualTo(email2.hashCode());
    }
}
