package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandNameTest {

    @DisplayName("BrandName 생성")
    @Nested
    class Create {

        @DisplayName("null, 빈 문자열, 공백은 허용하지 않는다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rejectsBlankValues(String value) {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new BrandName(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 값으로 생성할 수 있다")
        @Test
        void validNameCreatesSuccessfully() {
            // given
            String value = "나이키";

            // when
            BrandName name = new BrandName(value);

            // then
            assertThat(name.value()).isEqualTo(value);
        }
    }

    @DisplayName("동등성 비교")
    @Nested
    class Equals {

        @DisplayName("같은 값이면 동일하다")
        @Test
        void sameValueMeansEqual() {
            // given
            BrandName one = new BrandName("나이키");
            BrandName another = new BrandName("나이키");

            // when & then
            assertThat(one).isEqualTo(another);
            assertThat(one.hashCode()).isEqualTo(another.hashCode());
        }

        @DisplayName("다른 값이면 다르다")
        @Test
        void differentValueMeansNotEqual() {
            // given
            BrandName one = new BrandName("나이키");
            BrandName another = new BrandName("아디다스");

            // when & then
            assertThat(one).isNotEqualTo(another);
        }
    }
}
