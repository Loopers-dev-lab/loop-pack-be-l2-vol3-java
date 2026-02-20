package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class BrandTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenAllValuesAreValid() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";

            // act
            var brand = Brand.create(name, logoUrl, description);

            // assert
            assertAll(
                    () -> assertThat(brand.getName()).isEqualTo(name),
                    () -> assertThat(brand.getLogoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("이름이 정확히 2자이면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameIsExactlyTwoCharacters() {
            assertThatCode(() -> Brand.create("브랜", "logo url", "description"))
                    .doesNotThrowAnyException();
        }

        @DisplayName("이름이 정확히 50자이면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameIsExactlyFiftyCharacters() {
            // arrange
            var name = "a".repeat(50);

            // act & assert
            assertThatCode(() -> Brand.create(name, "logo url", "description"))
                    .doesNotThrowAnyException();
        }

        @DisplayName("설명이 null이면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsNull() {
            assertThatCode(() -> Brand.create("brand name", "logo url", null))
                    .doesNotThrowAnyException();
        }

        @DisplayName("설명이 빈 값이면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsEmpty() {
            assertThatCode(() -> Brand.create("brand name", "logo url", ""))
                    .doesNotThrowAnyException();
        }

        @DisplayName("이름이 null이면, REQUIRED_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsRequiredBrandNameException_whenNameIsNull() {
            assertThatThrownBy(() -> Brand.create(null, "logo url", "description"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_BRAND_NAME));
        }

        @DisplayName("이름이 2자 미만이면, INVALID_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsInvalidBrandNameException_whenNameIsTooShort() {
            assertThatThrownBy(() -> Brand.create("브", "logo url", "description"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_NAME));
        }

        @DisplayName("이름이 50자 초과이면, INVALID_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsInvalidBrandNameException_whenNameIsTooLong() {
            // arrange
            var name = "a".repeat(51);

            // act & assert
            assertThatThrownBy(() -> Brand.create(name, "logo url", "description"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_NAME));
        }

        @DisplayName("로고 URL이 null이면, REQUIRED_BRAND_LOGO_URL 예외가 발생한다.")
        @Test
        void throwsRequiredBrandLogoUrlException_whenLogoUrlIsNull() {
            assertThatThrownBy(() -> Brand.create("brand name", null, "description"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_BRAND_LOGO_URL));
        }
    }
}