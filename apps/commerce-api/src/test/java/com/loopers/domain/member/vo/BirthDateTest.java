package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthDateTest {

    @DisplayName("yyyy-MM-dd 형식의 생년월일은 생성에 성공한다.")
    @Test
    void create_success() {
        // given
        String birth = "1997-01-15";

        // when
        BirthDate birthDate = new BirthDate(birth);

        // then
        assertThat(birthDate.getValue()).isEqualTo(birth);
    }

    @DisplayName("잘못된 형식의 생년월일은 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"19970115", "1997/01/15", "01-15-1997", "1997-1-15", "1997-01-1"})
    void create_fail_invalid_format(String invalidBirth) {
        assertThatThrownBy(() -> new BirthDate(invalidBirth))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("null 또는 빈 문자열은 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void create_fail_empty(String emptyBirth) {
        assertThatThrownBy(() -> new BirthDate(emptyBirth))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("null은 예외가 발생한다.")
    @Test
    void create_fail_null() {
        assertThatThrownBy(() -> new BirthDate(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("유효하지 않은 날짜는 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"1997-13-01", "1997-02-30", "1997-00-15"})
    void create_fail_invalid_date(String invalidDate) {
        assertThatThrownBy(() -> new BirthDate(invalidDate))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("숫자만 포함된 문자열(yyyyMMdd)을 반환한다.")
    @Test
    void toPlainString() {
        // given
        BirthDate birthDate = new BirthDate("1997-01-15");

        // when
        String plain = birthDate.toPlainString();

        // then
        assertThat(plain).isEqualTo("19970115");
    }
}
