package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginIdTest {

    @Test
    void 로그인ID는_한글이_포함되면_BAD_REQUSET를_던진다() {
        // given
        String value = "루퍼스123loopers";

        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            LoginId.from(value);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    void 로그인ID가_영문과_숫자로만_이루어지면_객체생성_성공() {
        // given
        String value = "loopers123";

        // when
        LoginId loginId = LoginId.from(value);

        // then
        assertThat(loginId.asString()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"loop", "loopersloopersloopers"})
    void 로그인ID는_5자미만_20자초과이면_BAD_REQUEST를_던진다(String value) {
        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            LoginId.from(value);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
