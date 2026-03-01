package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class BrandNameTest {

    @DisplayName("브랜드 이름을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름이면, 정상적으로 생성된다.")
        @Test
        void createsBrandName_whenNameIsValid() {
            // arrange
            var name = "brand name";

            // act
            var brandName = new BrandName(name);

            // assert
            assertThat(brandName.getValue()).isEqualTo(name);
        }

        @DisplayName("이름 길이가 경계값이면, 정상적으로 생성된다.")
        @ParameterizedTest(name = "길이가 {0}인 이름")
        @ValueSource(ints = {2, 50})
        void createsBrandName_whenNameLengthIsAtBoundary(int length) {
            // arrange
            var name = "a".repeat(length);

            // act & assert
            assertThatCode(() -> new BrandName(name))
                    .doesNotThrowAnyException();
        }

        @DisplayName("이름이 null이면, REQUIRED_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsRequiredBrandNameException_whenNameIsNull() {
            assertThatThrownBy(() -> new BrandName(null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_BRAND_NAME));
        }

        @DisplayName("이름 길이가 유효하지 않으면, INVALID_BRAND_NAME 예외가 발생한다.")
        @ParameterizedTest(name = "길이가 {0}인 이름")
        @ValueSource(ints = {1, 51})
        void throwsInvalidBrandNameException_whenNameLengthIsInvalid(int length) {
            // arrange
            var name = "a".repeat(length);

            // act & assert
            assertThatThrownBy(() -> new BrandName(name))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_NAME));
        }
    }
}