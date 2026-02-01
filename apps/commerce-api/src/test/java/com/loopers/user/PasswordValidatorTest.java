package com.loopers.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PasswordValidatorTest {

    @Test
    public void 비밀번호_8자_미만이면_실패() {
        //given
        String password = "1234";

        //when
        boolean result = PasswordValidator.validate(password);

        //then
        assertThat(result).isFalse();
    }

    @Test
    public void 비밀번호_16자_초과하면_실패() {
        //given
        String password = "12345678912345678";

        //when
        boolean result = PasswordValidator.validate(password);

        //then
        assertThat(result).isFalse();
    }
}
