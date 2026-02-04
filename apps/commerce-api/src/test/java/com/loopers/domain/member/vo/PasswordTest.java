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

public class PasswordTest {

    @DisplayName("비밀번호 정책(8~16자, 영문/숫자/특수문자 포함)을 준수하면 생성에 성공한다.")
    @Test
    void create_success() {
        // given
        String pw = "PassWord123!";
        String birth = "1997-01-01";

        // when
        Password password = new Password(pw, birth);

        // then
        assertThat(password).isNotNull();
    }

    @DisplayName("비밀번호 길이가 8자 미만이거나, 16자 초과면 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"Short1!", "TooooooooooooLongPassword123!"})
    void create_fail_length(String invalidPw) {
        assertThatThrownBy(() -> new Password(invalidPw, "1997-01-01"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);

    }

    @DisplayName("비밀번호에 생년월일이 포함되면 예외가 발생한다.")
    @Test
    void create_fail_contains_birth() {
        // given
        String birth = "19970101";
        String invalidPw = "Test970101!";

        // when & then
        assertThatThrownBy(() -> new Password(invalidPw, birth))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("생년월일");
    }

    @DisplayName("비밀번호에 생년월일 패턴(YYYY, YY, MMDD)이 포함되면 예외가 발생한다.")
    @ParameterizedTest
    @CsvSource({
            "1997-12-31, Pass1997!@#",  // YYYY 포함
            "1997-12-31, Pass97!@#",    // YY 포함
            "1997-12-31, Pass1231!@#",  // MMDD 포함
            "1997-12-31, Pass971231!@#" // YYMMDD 포함
    })
    void create_fail_contains_birth_pattern(String birth, String invalidPw) {
        assertThatThrownBy(() -> new Password(invalidPw, birth))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("생년월일");
    }
}
