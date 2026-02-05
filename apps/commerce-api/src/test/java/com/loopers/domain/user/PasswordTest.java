package com.loopers.domain.user;

import static com.loopers.domain.user.UserFixture.createPasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class PasswordTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = createPasswordEncoder();
    }

    @DisplayName("Password를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("8자 이상 16자 이하이면, 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"Pass1!ab", "Password1!ab", "Password1!abcdef"})
        void createsPassword_whenLengthIsValid(String value) {
            assertThatCode(() -> new Password(value, passwordEncoder))
                    .doesNotThrowAnyException();
        }

        @DisplayName("길이가 유효하지 않으면, INVALID_PASSWORD_LENGTH 예외가 발생한다.")
        @ParameterizedTest
        @CsvSource({
                "Pass1!a",
                "Password1!abcdefg",
                "''"
        })
        void throwsInvalidPasswordLengthException_whenLengthIsInvalid(String password) {
            assertThatThrownBy(() -> new Password(password, passwordEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.INVALID_PASSWORD_LENGTH));
        }

        @DisplayName("허용되지 않은 문자가 포함되면, INVALID_PASSWORD_FORMAT 예외가 발생한다.")
        @ParameterizedTest
        @CsvSource({
                "Pass한글1!",
                "Pass 1!ab",
                "Pass(1!ab"
        })
        void throwsInvalidPasswordFormatException_whenContainsInvalidCharacter(String password) {
            assertThatThrownBy(() -> new Password(password, passwordEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT));
        }

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenValueIsNull() {
            assertThatThrownBy(() -> new Password(null, passwordEncoder))
                    .isInstanceOf(Exception.class);
        }
    }

    @DisplayName("matches 메서드를 호출할 때,")
    @Nested
    class Matches {

        @DisplayName("원본 비밀번호가 일치하면, true를 반환한다.")
        @Test
        void returnsTrue_whenRawPasswordMatches() {
            // arrange
            String rawPassword = "Password1!";
            Password password = new Password(rawPassword, passwordEncoder);

            // act
            boolean result = password.matches(rawPassword, passwordEncoder);

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("원본 비밀번호가 일치하지 않으면, false를 반환한다.")
        @Test
        void returnsFalse_whenRawPasswordDoesNotMatch() {
            // arrange
            String rawPassword = "Password1!";
            Password password = new Password(rawPassword, passwordEncoder);

            // act
            boolean result = password.matches("WrongPassword1!", passwordEncoder);

            // assert
            assertThat(result).isFalse();
        }
    }
}
