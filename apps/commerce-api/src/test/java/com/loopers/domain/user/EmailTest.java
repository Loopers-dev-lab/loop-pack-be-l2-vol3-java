package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("Email을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 형식이면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenFormatIsValid() {
            // act
            Email email = new Email("test@example.com");

            // assert
            assertThat(email.getValue()).isEqualTo("test@example.com");
        }

        @DisplayName("null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Email(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Email("  "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenFormatIsInvalid() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Email("invalid-email"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("동등성을 비교할 때, ")
    @Nested
    class Equality {

        @DisplayName("같은 값이면 동일한 객체로 판단한다.")
        @Test
        void isEqual_whenValuesAreSame() {
            // arrange
            Email email1 = new Email("test@example.com");
            Email email2 = new Email("test@example.com");

            // assert
            assertThat(email1).isEqualTo(email2);
            assertThat(email1.hashCode()).isEqualTo(email2.hashCode());
        }

        @DisplayName("다른 값이면 다른 객체로 판단한다.")
        @Test
        void isNotEqual_whenValuesAreDifferent() {
            // arrange
            Email email1 = new Email("test1@example.com");
            Email email2 = new Email("test2@example.com");

            // assert
            assertThat(email1).isNotEqualTo(email2);
        }
    }
}
