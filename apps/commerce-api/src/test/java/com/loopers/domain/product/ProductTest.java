package com.loopers.domain.product;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.vo.DisplayStatus;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductTest {

    @DisplayName("상품 생성")
    @Nested
    class Create {

        @DisplayName("유효한 Command가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenCommandIsValid() {
            // arrange
            ProductCommand.Create command = new ProductCommand.Create(1L, "나이키 에어맥스", 150000, 100);

            // act
            Product product = Product.create(1L, command);

            // assert
            assertAll(
                () -> assertThat(product.getId()).isNull(),
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName().value()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(product.getPrice().value()).isEqualTo(150000),
                () -> assertThat(product.getStock().value()).isEqualTo(100),
                () -> assertThat(product.getDisplayStatus()).isEqualTo(DisplayStatus.DISPLAYING)
            );
        }

        @DisplayName("이름이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsNull() {
            // arrange
            ProductCommand.Create command = new ProductCommand.Create(1L, null, 150000, 100);

            // act & assert
            assertThatThrownBy(() -> Product.create(1L, command))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("가격이 0이면 예외가 발생한다.")
        @Test
        void throwsException_whenPriceIsZero() {
            // arrange
            ProductCommand.Create command = new ProductCommand.Create(1L, "상품", 0, 100);

            // act & assert
            assertThatThrownBy(() -> Product.create(1L, command))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("재고가 음수이면 예외가 발생한다.")
        @Test
        void throwsException_whenStockIsNegative() {
            // arrange
            ProductCommand.Create command = new ProductCommand.Create(1L, "상품", 150000, -1);

            // act & assert
            assertThatThrownBy(() -> Product.create(1L, command))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("DB에서 복원할 때, ")
    @Nested
    class Reconstruct {

        @DisplayName("id를 포함한 모든 필드가 올바르게 복원된다.")
        @Test
        void reconstructsProduct_withAllFields() {
            // act
            Product product = Product.reconstruct(1L, 1L, "상품A", 10000, 50, DisplayStatus.DISPLAYING);

            // assert
            assertAll(
                () -> assertThat(product.getId()).isEqualTo(1L),
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName().value()).isEqualTo("상품A"),
                () -> assertThat(product.getPrice().value()).isEqualTo(10000),
                () -> assertThat(product.getStock().value()).isEqualTo(50),
                () -> assertThat(product.getDisplayStatus()).isEqualTo(DisplayStatus.DISPLAYING)
            );
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class Update {

        @DisplayName("유효한 Command가 주어지면, 상품 정보가 변경된다.")
        @Test
        void updatesProduct_whenCommandIsValid() {
            // arrange
            ProductCommand.Create createCommand = new ProductCommand.Create(1L, "나이키 에어맥스", 150000, 100);
            Product product = Product.create(1L, createCommand);
            ProductCommand.Update updateCommand = new ProductCommand.Update("수정상품", 20000, 30, DisplayStatus.NOT_DISPLAYING);

            // act
            product.update(updateCommand);

            // assert
            assertAll(
                () -> assertThat(product.getName().value()).isEqualTo("수정상품"),
                () -> assertThat(product.getPrice().value()).isEqualTo(20000),
                () -> assertThat(product.getStock().value()).isEqualTo(30),
                () -> assertThat(product.getDisplayStatus()).isEqualTo(DisplayStatus.NOT_DISPLAYING)
            );
        }

        @DisplayName("수정 시 이름이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUpdateNameIsNull() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "나이키 에어맥스", 150000, 100, DisplayStatus.DISPLAYING);
            ProductCommand.Update updateCommand = new ProductCommand.Update(null, 20000, 30, DisplayStatus.NOT_DISPLAYING);

            // act & assert
            assertThatThrownBy(() -> product.update(updateCommand))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("수정 시 가격이 0이면 예외가 발생한다.")
        @Test
        void throwsException_whenUpdatePriceIsZero() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "나이키 에어맥스", 150000, 100, DisplayStatus.DISPLAYING);
            ProductCommand.Update updateCommand = new ProductCommand.Update("상품", 0, 30, DisplayStatus.DISPLAYING);

            // act & assert
            assertThatThrownBy(() -> product.update(updateCommand))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("재고 차감")
    @Nested
    class DecreaseStock {

        @DisplayName("재고가 충분하면, 재고가 정상적으로 차감된다.")
        @Test
        void decreasesStock_whenSufficientStock() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "상품", 10000, 100, DisplayStatus.DISPLAYING);

            // act
            product.decreaseStock(30);

            // assert
            assertThat(product.getStock().value()).isEqualTo(70);
        }

        @DisplayName("재고가 부족하면 예외가 발생한다.")
        @Test
        void throwsException_whenInsufficientStock() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "상품", 10000, 10, DisplayStatus.DISPLAYING);

            // act & assert
            assertThatThrownBy(() -> product.decreaseStock(20))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("재고 증가")
    @Nested
    class IncreaseStock {

        @DisplayName("재고가 정상적으로 증가한다.")
        @Test
        void increasesStock() {
            // arrange
            Product product = Product.reconstruct(1L, 1L, "상품", 10000, 100, DisplayStatus.DISPLAYING);

            // act
            product.increaseStock(50);

            // assert
            assertThat(product.getStock().value()).isEqualTo(150);
        }
    }
}
