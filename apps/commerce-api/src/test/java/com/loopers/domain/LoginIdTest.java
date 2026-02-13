package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoginIdTest {

    @DisplayName("로그인 ID 객체를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("4~12자의 영문/숫자가 주어지면, 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"user123", "loop", "loopers2026"})
        void createLoginId_whenValidValue(String validValue) {
            LoginId loginId = new LoginId(validValue);
            assertThat(loginId.getValue()).isEqualTo(validValue);
        }

        @DisplayName("4자 미만이거나 12자를 초과하면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"abc", "longloginid123"})
        void createLoginId_whenLengthIsInvalid(String invalidLengthValue) {
            assertThatThrownBy(() -> new LoginId(invalidLengthValue))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("영문/숫자가 아닌 문자가 포함되면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"user!", "로그인id", "user 12"})
        void createLoginId_whenContainsInvalidChars(String invalidCharValue) {
            assertThatThrownBy(() -> new LoginId(invalidCharValue))
                    .isInstanceOf(CoreException.class);
        }
    }
}
