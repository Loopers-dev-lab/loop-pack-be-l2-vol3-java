package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class EmailTest {

    @Test
    void 이메일_형식을_따르지_않으면_BAD_REQUEST를_던진다() {
        // given
        String value = "testloopers.im";

        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            Email.from(value);
        });
    }

    @Test
    void 이메일_형식을_따르면_객체생성_성공() {
        // given
        String value = "test@loopers.im";

        // when
        Email email = Email.from(value);

        // then
        assertThat(email.asString()).isEqualTo(value);
    }

}
