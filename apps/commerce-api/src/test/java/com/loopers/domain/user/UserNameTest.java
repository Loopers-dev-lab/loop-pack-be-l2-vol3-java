package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class UserNameTest {

    @DisplayName("UserName을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값이면, 정상적으로 생성된다.")
        @Test
        void createsUserName_whenValueIsValid() {
            // arrange
            String value = "홍길동";

            // act & assert
            assertThatCode(() -> new UserName(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("null이거나 빈 값이면, REQUIRED_USER_NAME 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void throwsRequiredUserNameException_whenValueIsNullOrBlank(String value) {
            assertThatThrownBy(() -> new UserName(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.REQUIRED_USER_NAME));
        }
    }

    @DisplayName("마스킹된 이름을 반환할 때,")
    @Nested
    class Masked {

        @DisplayName("마지막 글자를 *로 마스킹한다.")
        @Test
        void returnsNameWithLastCharacterMasked() {
            // arrange
            UserName userName = new UserName("홍길동");

            // act
            String masked = userName.masked();

            // assert
            assertThat(masked).isEqualTo("홍길*");
        }

        @DisplayName("한 글자인 경우, *로 반환한다.")
        @Test
        void returnsAsterisk_whenNameHasSingleCharacter() {
            // arrange
            UserName userName = new UserName("홍");

            // act
            String masked = userName.masked();

            // assert
            assertThat(masked).isEqualTo("*");
        }
    }
}
