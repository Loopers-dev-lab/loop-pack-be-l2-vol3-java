package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NameTest {

    @DisplayName("유효한 이름은 생성에 성공한다.")
    @ParameterizedTest
    @ValueSource(strings = {"홍길동", "김", "Andrew", "김앤드류"})
    void create_success(String validName) {
        // when
        Name name = new Name(validName);

        // then
        assertThat(name.getValue()).isEqualTo(validName);
    }

    @DisplayName("null 또는 빈 문자열은 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void create_fail_empty(String emptyName) {
        assertThatThrownBy(() -> new Name(emptyName))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("null은 예외가 발생한다.")
    @Test
    void create_fail_null() {
        assertThatThrownBy(() -> new Name(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("이름의 마지막 글자를 마스킹하여 반환한다.")
    @ParameterizedTest
    @CsvSource({
            "홍길동, 홍길*",
            "김, *",
            "Andrew, Andre*",
            "AB, A*"
    })
    void masked(String original, String expected) {
        // given
        Name name = new Name(original);

        // when
        String masked = name.masked();

        // then
        assertThat(masked).isEqualTo(expected);
    }
}
