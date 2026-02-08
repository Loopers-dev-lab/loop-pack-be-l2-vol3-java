package com.loopers.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void 이름의_마지막_글자가_마스킹된다() {
        // given
        User user = User.builder()
                .loginId("testId")
                .password("password123!")
                .name("홍길동")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        // when
        String maskedName = user.getMaskedName();

        // then
        assertThat(maskedName).isEqualTo("홍길*");
    }

    @Test
    void 한_글자_이름은_마스킹_문자로_반환된다() {
        // given
        User user = User.builder()
                .loginId("testId")
                .password("password123!")
                .name("김")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        // when
        String maskedName = user.getMaskedName();

        // then
        assertThat(maskedName).isEqualTo("*");
    }

    @Test
    void 두_글자_이름의_마지막_글자가_마스킹된다() {
        // given
        User user = User.builder()
                .loginId("testId")
                .password("password123!")
                .name("이순")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        // when
        String maskedName = user.getMaskedName();

        // then
        assertThat(maskedName).isEqualTo("이*");
    }

    @Test
    void changePassword로_비밀번호가_변경된다() {
        // given
        User user = User.builder()
                .loginId("testId")
                .password("oldPassword123!")
                .name("홍길동")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();
        String newPassword = "newPassword456!";

        // when
        user.changePassword(newPassword);

        // then
        assertThat(user.getPassword()).isEqualTo(newPassword);
    }
}
