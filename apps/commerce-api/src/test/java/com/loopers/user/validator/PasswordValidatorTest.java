package com.loopers.user.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class PasswordValidatorTest {


    @Test
    void 비밀번호가_8자_미만이면_IllegalArgumentException이_발생한다() {
        //given
        String password = "1234";

        //when
        Throwable thrown = catchThrowable(() -> PasswordValidator.validate(password, null));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비밀번호가_16자_초과하면_IllegalArgumentException이_발생한다() {
        //given
        String password = "12345678901234567";

        //when
        Throwable thrown = catchThrowable(() -> PasswordValidator.validate(password, null));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password1!한글", "Password1!😀", "Password 1!"})
    void 비밀번호에_허용되지_않는_문자_포함시_IllegalArgumentException이_발생한다(String password) {
        //given

        //when
        Throwable thrown = catchThrowable(() -> PasswordValidator.validate(password, null));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비밀번호에_생년월일_포함시_IllegalArgumentException이_발생한다() {
        //given
        String birthDate = "1990-04-27";
        String password = "pass1990-04-27";

        //when
        Throwable thrown = catchThrowable(() -> PasswordValidator.validate(password, birthDate));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
