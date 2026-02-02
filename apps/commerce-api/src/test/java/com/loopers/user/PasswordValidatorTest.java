package com.loopers.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234한글", "abcd1234 ", "abcd1234💬"})
    public void 허용되지_않는_문자_포함시_실패(String password) {

        //when
        boolean result = PasswordValidator.validate(password);

        //then
        assertThat(result).isFalse();
    }

    @Test
    public void 비밀번호에_생년월일_포함시_실패() {
        //given
        String password = "12319900427";
        String birthDate = "19900427";

        //when
        boolean result = PasswordValidator.validate(password, birthDate);

        //then
        assertThat(result).isFalse();
    }
}
