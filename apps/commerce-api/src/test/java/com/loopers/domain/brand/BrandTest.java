package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    private static final String VALID_BRAND_NAME = "아디다스";

    @DisplayName("브랜드를 생성할 때")
    @Nested
    class Create {

        @DisplayName("모든 정보가 올바르면, 브랜드가 정상적으로 생성된다.")
        @Test
        void createsBrand_whenAllInfoIsValid() {
            // act
            Brand brand = new Brand(VALID_BRAND_NAME);

            // assert
            assertThat(brand.getName()).isEqualTo(VALID_BRAND_NAME);
        }

        @DisplayName("브랜드명이 null이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Brand(null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드명이 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Brand("  ");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 정보를 수정할 때")
    @Nested
    class Update {

        @DisplayName("올바른 브랜드명으로 수정하면, 브랜드명이 변경된다.")
        @Test
        void updatesBrandName_whenNewNameIsValid() {
            // arrange
            Brand brand = new Brand(VALID_BRAND_NAME);
            String newName = "나이키";

            // act
            brand.update(newName);

            // assert
            assertThat(brand.getName()).isEqualTo(newName);
        }

        @DisplayName("브랜드명이 null이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewNameIsNull() {
            // arrange
            Brand brand = new Brand(VALID_BRAND_NAME);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update(null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드명이 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewNameIsBlank() {
            // arrange
            Brand brand = new Brand(VALID_BRAND_NAME);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update("  ");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
