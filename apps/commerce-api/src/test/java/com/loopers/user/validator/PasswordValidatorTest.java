package com.loopers.user.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class PasswordValidatorTest {

    private PasswordValidator passwordValidator;

    @BeforeEach
    void setUp() {
        passwordValidator = new PasswordValidator();
    }

    @Test
    void 비밀번호가_8자_미만이면_예외_발생() {
        //given
        String password = "1234";

        //when
        Throwable thrown = catchThrowable(() -> passwordValidator.validate(password, null));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비밀번호가_16자_초과하면_예외_발생() {
        //given
        String password = "12345678901234567";

        //when
        Throwable thrown = catchThrowable(() -> passwordValidator.validate(password, null));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password1!한글", "Password1!😀", "Password 1!"})
    void 비밀번호에_허용되지_않는_문자_포함시_예외_발생(String password) {
        //given

        //when
        Throwable thrown = catchThrowable(() -> passwordValidator.validate(password, null));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비밀번호에_생년월일_포함시_예외_발생() {
        //given
        String birthDate = "19900427";
        String password = "pass19900427";

        //when
        Throwable thrown = catchThrowable(() -> passwordValidator.validate(password, birthDate));

        //then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
