package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class BirthDateTest {

    @Test
    void 생년월일이_미래면_BAD_REQUEST를_던진다() {
        // given
        LocalDate value = LocalDate.now().plusDays(1);

        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            BirthDate.from(value);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    void 생년월일이_오늘날짜_이전이면_정상적으로_생성() {
        // given
        LocalDate value = LocalDate.of(1996, 11, 22);

        // when
        BirthDate birthDate = BirthDate.from(value);

        // then
        assertThat(birthDate.asLocalDate()).isEqualTo(value);
    }
}
