package com.loopers.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class PasswordTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1993, 1, 1);

    @Test
    void 유효한_비밀번호_생성_성공(){
        Password password = Password.of("Valid1234!!!", BIRTHDAY);

        assertThat(password.getValue()).isEqualTo("Valid1234!!!");
    }

    @Test
    void 비밀번호_null_이면_비밀번호_필수_입력값(){
        assertThatThrownBy(() -> Password.of(null, BIRTHDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호는 필수 입력값입니다.");
    }

    @Test
    void 비밀번호_8자리_미만이면_예외(){
        assertThatThrownBy(() -> Password.of("Va234!", BIRTHDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호는 8~16자리 영문 대소문자, 숫자, 특수문자만 가능합니다.");
    }

    @Test
    void 비밀번호_16자리_초과면_예외(){
        assertThatThrownBy(() -> Password.of("sdfasdfacdfsdfsdver!", BIRTHDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호는 8~16자리 영문 대소문자, 숫자, 특수문자만 가능합니다.");
    }

    @Test
    void 생년월일_yyyyMMdd_포함시_예외(){
        assertThatThrownBy(() -> Password.of("19930101sisd!!!", BIRTHDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("생년월일");
    }
    @Test
    void 생년월일_yyMMdd_포함시_예외(){
        assertThatThrownBy(() -> Password.of("930101sisd!!!", BIRTHDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("생년월일");
    }
}
