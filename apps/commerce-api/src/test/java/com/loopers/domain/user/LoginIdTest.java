package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdTest {

    @DisplayName("LoginId를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("영문과 숫자로 구성된 값이면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenValueIsAlphanumeric() {
            // act
            LoginId loginId = new LoginId("testUser1");

            // assert
            assertThat(loginId.getValue()).isEqualTo("testUser1");
        }

        @DisplayName("null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId("  "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueContainsSpecialChars() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId("user@123"));

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
            LoginId loginId1 = new LoginId("testUser1");
            LoginId loginId2 = new LoginId("testUser1");

            // assert
            assertThat(loginId1).isEqualTo(loginId2);
            assertThat(loginId1.hashCode()).isEqualTo(loginId2.hashCode());
        }

        @DisplayName("다른 값이면 다른 객체로 판단한다.")
        @Test
        void isNotEqual_whenValuesAreDifferent() {
            // arrange
            LoginId loginId1 = new LoginId("testUser1");
            LoginId loginId2 = new LoginId("testUser2");

            // assert
            assertThat(loginId1).isNotEqualTo(loginId2);
        }
    }
}
