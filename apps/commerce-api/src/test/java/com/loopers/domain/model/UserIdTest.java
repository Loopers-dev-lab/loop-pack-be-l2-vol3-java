package com.loopers.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;


class UserIdTest {



    @Test
    @DisplayName("유효한 로그인 ID 생성 성공")
    void create_success() {
        //given
        String value = "testid1234";

        //when
        UserId userId = UserId.of(value);

        //then
        assertThat(userId.getValue()).isEqualTo("testid1234");
    }

    @Test
    @DisplayName("로그인 ID null이면 예외")
    void create_null() {
        //given
        String value = null;

        //when and then
       assertThatThrownBy(() -> UserId.of(value))
               .isInstanceOf(IllegalArgumentException.class)
               .hasMessage("로그인 ID는 필수 입력값입니다.");

    }

    @Test
    @DisplayName("로그인 ID 4자 미만이면 예외")
    void create_fail_too_short() {
        // given
        String value = "abc";

        // when and then
        assertThatThrownBy(() -> UserId.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4~10자");
    }

    @Test
    @DisplayName("로그인 ID 10자 초과면 예외")
    void create_fail_too_long() {
        // given
        String value = "abcdefghijk";

        // when & then
        assertThatThrownBy(() -> UserId.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4~10자");
    }

    @Test
    @DisplayName("로그인 ID 특수문자 포함시 예외")
    void create_fail_special_char() {
        // given
        String value = "test@123";

        // when and then
        assertThatThrownBy(() -> UserId.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("영문");
    }

    @Test
    @DisplayName("로그인 ID 공백 trim 처리")
    void create_success_with_trim() {
        // given
        String value = "  test1234  ";

        // when
        UserId userId = UserId.of(value);

        // then
        assertThat(userId.getValue()).isEqualTo("test1234");
    }

    @Test
    @DisplayName("로그인 ID 4자 성공 (최소 경계)")
    void create_success_min_length() {
        UserId userId = UserId.of("abcd");
        assertThat(userId.getValue()).isEqualTo("abcd");
    }

    @Test
    @DisplayName("로그인 ID 10자 성공 (최대 경계)")
    void create_success_max_length() {
        UserId userId = UserId.of("abcdefghij");
        assertThat(userId.getValue()).isEqualTo("abcdefghij");
    }

    @Test
    @DisplayName("로그인 ID 대문자 포함시 예외")
    void create_fail_uppercase() {
        //give
        String value = "TEST1234";
        //when and then
        assertThatThrownBy(() -> UserId.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("영문 소문자");
    }

}
