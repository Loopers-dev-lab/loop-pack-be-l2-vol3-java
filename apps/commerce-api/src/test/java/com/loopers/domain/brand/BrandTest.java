package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BrandTest {

    @DisplayName("브랜드 생성 시, ")
    @Nested
    class Create {
        @DisplayName("name과 description이 유효하면 정상적으로 생성된다.")
        @Test
        void createsBrand_whenFieldsAreValid() {
            // act
            Brand brand = Brand.create("나이키", "Just Do It!");

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("나이키"),
                () -> assertThat(brand.getDescription()).isEqualTo("Just Do It!")
            );
        }

        @DisplayName("description이 null이어도 정상적으로 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsNull() {
            // act
            Brand brand = Brand.create("나이키", null);

            // assert
            assertThat(brand.getDescription()).isNull();
        }

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Brand.create(null, "설명");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Brand.create("", "설명");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 공백 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Brand.create("   ", "설명");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 수정 시, ")
    @Nested
    class Update {
        @DisplayName("name과 description을 변경할 수 있다.")
        @Test
        void updatesBrand_whenFieldsAreValid() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            // act
            brand.update("아디다스", "독일 스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("아디다스"),
                () -> assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드")
            );
        }

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUpdateNameIsNull() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update(null, "설명");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUpdateNameIsBlank() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update("  ", "설명");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}