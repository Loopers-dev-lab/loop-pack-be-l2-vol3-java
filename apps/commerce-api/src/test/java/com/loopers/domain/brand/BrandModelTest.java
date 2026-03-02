package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("name과 description이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenAllRequiredFieldsAreProvided() {
            // arrange
            String name = "나이키";
            String description = "스포츠 의류 및 신발 브랜드";

            // act
            BrandModel brand = new BrandModel(name, description);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("name이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String name = "   ";
            String description = "스포츠 의류 및 신발 브랜드";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandModel(name, description);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("description이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDescriptionIsBlank() {
            // arrange
            String name = "나이키";
            String description = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandModel(name, description);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("name과 description이 주어지면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenValidFieldsAreProvided() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            String newName = "아디다스";
            String newDescription = "독일 스포츠 브랜드";

            // act
            brand.update(newName, newDescription);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(newName),
                () -> assertThat(brand.getDescription()).isEqualTo(newDescription)
            );
        }

        @DisplayName("name이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update("   ", "독일 스포츠 브랜드");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("description이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDescriptionIsBlank() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update("아디다스", "   ");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("삭제하면, deletedAt이 설정된다.")
        @Test
        void setsDeletedAt_whenDeleted() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");

            // act
            brand.delete();

            // assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenDeletedTwice() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            brand.delete();
            var firstDeletedAt = brand.getDeletedAt();

            // act
            brand.delete();

            // assert
            assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}
