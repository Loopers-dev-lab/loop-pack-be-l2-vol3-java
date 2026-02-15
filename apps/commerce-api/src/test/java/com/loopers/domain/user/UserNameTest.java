package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserNameTest {

    @DisplayName("UserName을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 이름이면, 정상적으로 생성된다.")
        @Test
        void createsUserName_whenValueIsValid() {
            // act
            UserName userName = new UserName("홍길동");

            // assert
            assertThat(userName.getValue()).isEqualTo("홍길동");
        }

        @DisplayName("null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserName(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserName("  "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름을 마스킹할 때, ")
    @Nested
    class Mask {

        @DisplayName("마지막 글자가 '*'로 대체된다.")
        @Test
        void masksLastCharacter() {
            // arrange
            UserName userName = new UserName("홍길동");

            // act & assert
            assertThat(userName.mask()).isEqualTo("홍길*");
        }

        @DisplayName("이름이 한 글자이면, '*'로 대체된다.")
        @Test
        void masksSingleCharacterName() {
            // arrange
            UserName userName = new UserName("홍");

            // act & assert
            assertThat(userName.mask()).isEqualTo("*");
        }

        @DisplayName("이름이 두 글자이면, 마지막 글자만 '*'로 대체된다.")
        @Test
        void masksTwoCharacterName() {
            // arrange
            UserName userName = new UserName("홍길");

            // act & assert
            assertThat(userName.mask()).isEqualTo("홍*");
        }
    }

    @DisplayName("동등성을 비교할 때, ")
    @Nested
    class Equality {

        @DisplayName("같은 값이면 동일한 객체로 판단한다.")
        @Test
        void isEqual_whenValuesAreSame() {
            // arrange
            UserName name1 = new UserName("홍길동");
            UserName name2 = new UserName("홍길동");

            // assert
            assertThat(name1).isEqualTo(name2);
            assertThat(name1.hashCode()).isEqualTo(name2.hashCode());
        }

        @DisplayName("다른 값이면 다른 객체로 판단한다.")
        @Test
        void isNotEqual_whenValuesAreDifferent() {
            // arrange
            UserName name1 = new UserName("홍길동");
            UserName name2 = new UserName("김철수");

            // assert
            assertThat(name1).isNotEqualTo(name2);
        }
    }
}
