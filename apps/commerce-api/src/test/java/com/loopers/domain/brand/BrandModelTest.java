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

    @DisplayName("브랜드 생성")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 주어지면 정상 생성된다")
        @Test
        void createsWithNameAndDescription() {
            // arrange
            String name = "나이키";
            String description = "스포츠 브랜드";
            // act
            BrandModel brand = new BrandModel(name, description);
            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("설명 없이 이름만으로 생성할 수 있다")
        @Test
        void createsWithNameOnly() {
            // arrange & act
            BrandModel brand = new BrandModel("나이키", null);
            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("나이키"),
                () -> assertThat(brand.getDescription()).isNull()
            );
        }

        @DisplayName("이름이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNullName() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BrandModel(null, "설명");
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnBlankName() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BrandModel("   ", "설명");
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class Update {

        @DisplayName("이름과 설명을 수정할 수 있다")
        @Test
        void updatesNameAndDescription() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");
            // act
            brand.update("아디다스", "독일 스포츠 브랜드");
            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("아디다스"),
                () -> assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드")
            );
        }

        @DisplayName("수정 시 이름이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNullNameUpdate() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                brand.update(null, "설명");
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수정 시 이름이 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnBlankNameUpdate() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                brand.update("  ", "설명");
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
