package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class EmailTest {

    @DisplayName("Email을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("이메일 형식이 올바르면, 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"user@domain.com", "user@domain.co"})
        void createsEmail_whenFormatIsValid(String value) {
            assertThatCode(() -> new Email(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("유효하지 않은 이메일 형식이면, INVALID_EMAIL_FORMAT 예외가 발생한다.")
        @ParameterizedTest
        @CsvSource({
                "userdomain.com",
                "user@domaincom",
                "user@domain.c",
                "@domain.com",
                "''"
        })
        void throwsInvalidEmailFormatException_whenFormatIsInvalid(String email) {
            assertThatThrownBy(() -> new Email(email))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.INVALID_EMAIL_FORMAT));
        }

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenValueIsNull() {
            assertThatThrownBy(() -> new Email(null))
                    .isInstanceOf(Exception.class);
        }
    }
}
