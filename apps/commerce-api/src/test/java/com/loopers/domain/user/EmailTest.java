package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class EmailTest {

    @DisplayName("Email을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("이메일 형식이 올바르면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenFormatIsValid() {
            // arrange
            String value = "user@domain.com";

            // act & assert
            assertThatCode(() -> new Email(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("이메일에 @가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenMissingAtSign() {
            // arrange
            String value = "userdomain.com";

            // act & assert
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("이메일 도메인에 점이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenMissingDotInDomain() {
            // arrange
            String value = "user@domaincom";

            // act & assert
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}