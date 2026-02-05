package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class NameTest {

    @ParameterizedTest
    @ValueSource(strings = {"김", "김가나다라마바"})
    void 이름이_2자미만_6자초과이면_BAD_REQUEST를_던진다(String value) {
        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            Name.from(value);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"김loop", "김123"})
    void 이름에_영어나_숫자가_포함되면_BAD_REQUEST를_던진다(String value) {
        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            Name.from(value);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    void 이름이_한글_2자이상_6자이하이면_객체생성_성공() {
        // given
        String value = "루퍼스";

        // when
        Name name = Name.from(value);

        // then
        assertThat(name.asString()).isEqualTo(value);
    }
}
