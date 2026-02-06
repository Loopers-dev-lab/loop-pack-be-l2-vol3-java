package com.loopers.domain.model;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserNameTest {
    @Test
    @DisplayName("유효한 이름 생성 성공 - 한글")
    void create_success_korean() {
        // given
        String value = "홍길동";

        // when
        UserName userName = UserName.of(value);

        // then
        assertThat(userName.getValue()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("유효한 이름 생성 성공 - 영문")
    void create_success_english() {
        // given
        String value = "John";

        // when
        UserName userName = UserName.of(value);

        // then
        assertThat(userName.getValue()).isEqualTo("John");
    }

    @Test
    @DisplayName("이름 null이면 예외")
    void create_fail_null() {
        // given
        String value = null;

        // when & then
        assertThatThrownBy(() -> UserName.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 필수 값입니다.");
    }

    @Test
    @DisplayName("이름 2자 미만이면 예외")
    void create_fail_too_short() {
        // given
        String value = "홍";

        // when & then
        assertThatThrownBy(() -> UserName.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2~20자");
    }

    @Test
    @DisplayName("이름 20자 초과면 예외")
    void create_fail_too_long() {
        // given
        String value = "가나다라마바사아자차카타파하가나다라마바사";

        // when & then
        assertThatThrownBy(() -> UserName.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2~20자");
    }

    @Test
    @DisplayName("이름 특수문자 포함시 예외")
    void create_fail_special_char() {
        // given
        String value = "홍길동!";

        // when & then
        assertThatThrownBy(() -> UserName.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("한글 또는 영문");
    }

}
