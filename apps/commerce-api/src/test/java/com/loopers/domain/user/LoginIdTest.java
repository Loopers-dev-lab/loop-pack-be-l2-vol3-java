package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class LoginIdTest {

    @DisplayName("LoginId를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("영문과 숫자로만 이루어져 있으면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenAlphanumeric() {
            // arrange
            String value = "user123";

            // act & assert
            assertThatCode(() -> new LoginId(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("단일 문자여도, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenSingleCharacter() {
            // arrange
            String value = "a";

            // act & assert
            assertThatCode(() -> new LoginId(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("숫자만으로 이루어져 있어도, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenOnlyDigits() {
            // arrange
            String value = "123456";

            // act & assert
            assertThatCode(() -> new LoginId(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("영문과 숫자 외의 문자가 포함되면, INVALID_LOGIN_ID_FORMAT 예외가 발생한다.")
        @ParameterizedTest(name = "[{index}] {1}")
        @CsvSource({
                "user@123, 특수문자(@)",
                "user한글, 한글",
                "'user 123', 공백",
                "'', 빈 문자열"
        })
        void throwsInvalidLoginIdFormatException_whenContainsInvalidCharacter(String loginId, String description) {
            assertThatThrownBy(() -> new LoginId(loginId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT));
        }

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenValueIsNull() {
            assertThatThrownBy(() -> new LoginId(null))
                    .isInstanceOf(Exception.class);
        }
    }
}
