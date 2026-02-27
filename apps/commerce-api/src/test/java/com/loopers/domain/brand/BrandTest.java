package com.loopers.domain.brand;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandTest {

    @DisplayName("브랜드 생성")
    @Nested
    class Create {

        @DisplayName("유효한 Command가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenCommandIsValid() {
            // arrange
            BrandCommand.Create command = new BrandCommand.Create("나이키", "스포츠 브랜드");
            // act
            Brand brand = Brand.create(command);
            // assert
            assertAll(
                () -> assertThat(brand.getId()).isNull(),
                () -> assertThat(brand.getName().value()).isEqualTo("나이키"),
                () -> assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("설명이 null이어도 정상적으로 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsNull() {
            // arrange
            BrandCommand.Create command = new BrandCommand.Create("나이키", null);

            // act
            Brand brand = Brand.create(command);

            // assert
            assertAll(
                () -> assertThat(brand.getName().value()).isEqualTo("나이키"),
                () -> assertThat(brand.getDescription()).isNull()
            );
        }

        @DisplayName("이름이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsNull() {
            // arrange
            BrandCommand.Create command = new BrandCommand.Create(null, "설명");

            // act & assert
            assertThatThrownBy(() -> Brand.create(command))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("이름이 빈 값이면 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsBlank() {
            // arrange
            BrandCommand.Create command = new BrandCommand.Create("  ", "설명");

            // act & assert
            assertThatThrownBy(() -> Brand.create(command))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("DB에서 복원할 때, ")
    @Nested
    class Reconstruct {

        @DisplayName("id를 포함한 모든 필드가 올바르게 복원된다.")
        @Test
        void reconstructsBrand_withAllFieldsIncludingId() {
            // act
            Brand brand = Brand.reconstruct(1L, "아디다스", "독일 스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(brand.getId()).isEqualTo(1L),
                () -> assertThat(brand.getName().value()).isEqualTo("아디다스"),
                () -> assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드")
            );
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 Command가 주어지면, 이름과 설명이 변경된다.")
        @Test
        void updatesBrand_whenCommandIsValid() {
            // arrange
            Brand brand = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            BrandCommand.Update command = new BrandCommand.Update("뉴발란스", "미국 스포츠 브랜드");

            // act
            brand.update(command);

            // assert
            assertAll(
                () -> assertThat(brand.getName().value()).isEqualTo("뉴발란스"),
                () -> assertThat(brand.getDescription()).isEqualTo("미국 스포츠 브랜드")
            );
        }

        @DisplayName("수정 시 이름이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUpdateNameIsNull() {
            // arrange
            Brand brand = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            BrandCommand.Update command = new BrandCommand.Update(null, "설명");

            // act & assert
            assertThatThrownBy(() -> brand.update(command))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("수정 시 이름이 빈 값이면 예외가 발생한다.")
        @Test
        void throwsException_whenUpdateNameIsBlank() {
            // arrange
            Brand brand = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            BrandCommand.Update command = new BrandCommand.Update("  ", "설명");

            // act & assert
            assertThatThrownBy(() -> brand.update(command))
                .isInstanceOf(CoreException.class);
        }
    }
}
