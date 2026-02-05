package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NameTest {

    @DisplayName("이름 객체를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("2자 이상 10자 이하의 이름이 주어지면, 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"홍길", "홍길동", "가나다라마바사아자차"})
        void createName_whenValidNameProvided(String validNameValue) {
            // act
            Name name = new Name(validNameValue);

            // assert
            assertThat(name).isNotNull();
        }

        @DisplayName("이름이 null이면 예외가 발생한다.")
        @Test
        void createName_whenNameIsNull() {
            assertThatThrownBy(() -> new Name(null))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이름이 빈 문자열이거나 공백이면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        void createName_whenNameIsBlank(String blankName) {
            assertThatThrownBy(() -> new Name(blankName))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이름이 2자 미만이면 예외가 발생한다.")
        @Test
        void createName_whenNameIsTooShort() {
            String shortName = "가";

            assertThatThrownBy(() -> new Name(shortName))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이름이 10자를 초과하면 예외가 발생한다.")
        @Test
        void createName_whenNameIsTooLong() {
            String longName = "가나다라마바사아자차카"; // 11자

            assertThatThrownBy(() -> new Name(longName))
                    .isInstanceOf(CoreException.class);}
    }
}
