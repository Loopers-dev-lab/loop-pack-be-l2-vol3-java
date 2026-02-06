package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordValidatorTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(2000, 1, 15);

    @Nested
    class 검증 {

        @ParameterizedTest
        @ValueSource(strings = {
                "Abcd123!",          // 최소 길이 (8자)
                "Abcd1234!Abcd12",   // 최대 길이 (16자)
                "Abcd1234!"          // 일반 케이스
        })
        void 유효한_비밀번호면_통과한다(String password) {
            assertThatCode(() -> PasswordValidator.validate(password, BIRTHDAY))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 비밀번호가_null_또는_빈값이면_예외(String password) {
            assertThatThrownBy(() -> PasswordValidator.validate(password, BIRTHDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("비밀번호");
        }

        @Test
        void 비밀번호가_8자_미만이면_예외() {
            assertThatThrownBy(() -> PasswordValidator.validate("Abcd123", BIRTHDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("비밀번호는 8~16자여야 합니다");
        }

        @Test
        void 비밀번호가_16자_초과면_예외() {
            assertThatThrownBy(() -> PasswordValidator.validate("Abcd1234!Abcd1234", BIRTHDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("비밀번호는 8~16자여야 합니다");
        }

        @Test
        void 비밀번호에_허용되지_않은_문자가_포함되면_예외() {
            assertThatThrownBy(() -> PasswordValidator.validate("Abcd1234!가", BIRTHDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("비밀번호는 영문/숫자/특수문자만 가능합니다");
        }

        @Test
        void 생년월일이_null이면_생년월일_검증을_스킵한다() {
            assertThatCode(() -> PasswordValidator.validate("Abcd1234!", null))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Abcd20000115!", "Abcd000115!!", "Abcd0115"})
        void 비밀번호에_생년월일이_포함되면_예외(String password) {
            assertThatThrownBy(() -> PasswordValidator.validate(password, BIRTHDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("비밀번호에 생년월일을 포함할 수 없습니다");
        }
    }
}
