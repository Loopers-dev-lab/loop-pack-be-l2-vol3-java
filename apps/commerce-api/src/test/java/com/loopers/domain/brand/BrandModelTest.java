package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrandModelTest {

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("브랜드명이 주어지면, 정상적으로 생성된다.")
        @Test
        void createBrandModel_whenNameProvided() {
            // act
            BrandModel brand = BrandModel.create("Nike");

            // assert
            assertThat(brand.getName()).isEqualTo("Nike");
        }

        @DisplayName("브랜드명이 null이면 예외가 발생한다.")
        @Test
        void createBrandModel_whenNameIsNull() {
            assertThatThrownBy(() -> BrandModel.create(null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명은 필수값입니다.");
        }

        @DisplayName("브랜드명이 빈 문자열이면 예외가 발생한다.")
        @Test
        void createBrandModel_whenNameIsBlank() {
            assertThatThrownBy(() -> BrandModel.create("  "))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명은 필수값입니다.");
        }

        @DisplayName("브랜드명이 100자 이상이면 예외가 발생한다.")
        @Test
        void createBrandModel_whenNameTooLong() {
            String longName = "a".repeat(100);

            assertThatThrownBy(() -> BrandModel.create(longName))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명은 99자 이하여야 합니다.");
        }
    }

    @DisplayName("브랜드명을 변경할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 브랜드명이 주어지면, 정상적으로 변경된다.")
        @Test
        void updateBrandModel_whenNameProvided() {
            // arrange
            BrandModel brand = BrandModel.create("Nike");

            // act
            brand.update("Adidas");

            // assert
            assertThat(brand.getName()).isEqualTo("Adidas");
        }

        @DisplayName("브랜드명이 null이면 예외가 발생한다.")
        @Test
        void updateBrandModel_whenNameIsNull() {
            // arrange
            BrandModel brand = BrandModel.create("Nike");

            // act & assert
            assertThatThrownBy(() -> brand.update(null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명은 필수값입니다.");
        }

        @DisplayName("브랜드명이 빈 문자열이면 예외가 발생한다.")
        @Test
        void updateBrandModel_whenNameIsBlank() {
            // arrange
            BrandModel brand = BrandModel.create("Nike");

            // act & assert
            assertThatThrownBy(() -> brand.update("  "))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명은 필수값입니다.");
        }

        @DisplayName("브랜드명이 100자 이상이면 예외가 발생한다.")
        @Test
        void updateBrandModel_whenNameTooLong() {
            // arrange
            BrandModel brand = BrandModel.create("Nike");
            String longName = "a".repeat(100);

            // act & assert
            assertThatThrownBy(() -> brand.update(longName))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명은 99자 이하여야 합니다.");
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("delete() 호출 시 deletedAt이 설정된다.")
        @Test
        void delete_whenCalled() {
            // arrange
            BrandModel brand = BrandModel.create("Nike");

            // act
            brand.delete();

            // assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }
    }
}
