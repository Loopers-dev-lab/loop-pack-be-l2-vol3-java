package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelTest {

    @DisplayName("재고 생성")
    @Nested
    class Create {

        @DisplayName("상품ID와 수량으로 정상 생성된다")
        @Test
        void createsSuccessfully() {
            // arrange & act
            StockModel stock = new StockModel(1L, 100);
            // assert
            assertAll(
                () -> assertThat(stock.getProductId()).isEqualTo(1L),
                () -> assertThat(stock.getQuantity()).isEqualTo(100)
            );
        }

        @DisplayName("수량이 0이면 정상 생성된다")
        @Test
        void createsWithZeroQuantity() {
            // arrange & act
            StockModel stock = new StockModel(1L, 0);
            // assert
            assertThat(stock.getQuantity()).isEqualTo(0);
        }

        @DisplayName("수량이 음수면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNegativeQuantity() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                new StockModel(1L, -1);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 차감")
    @Nested
    class Decrease {

        @DisplayName("충분한 재고가 있으면 차감된다")
        @Test
        void decreasesSuccessfully() {
            // arrange
            StockModel stock = new StockModel(1L, 100);
            // act
            stock.decrease(30);
            // assert
            assertThat(stock.getQuantity()).isEqualTo(70);
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnInsufficientStock() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                stock.decrease(11);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고를 정확히 0까지 차감할 수 있다")
        @Test
        void decreasesToZero() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            // act
            stock.decrease(10);
            // assert
            assertThat(stock.getQuantity()).isEqualTo(0);
        }
    }

    @DisplayName("재고 수정")
    @Nested
    class Update {

        @DisplayName("수량을 수정할 수 있다")
        @Test
        void updatesQuantity() {
            // arrange
            StockModel stock = new StockModel(1L, 100);
            // act
            stock.update(50);
            // assert
            assertThat(stock.getQuantity()).isEqualTo(50);
        }

        @DisplayName("음수로 수정하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNegativeQuantityUpdate() {
            // arrange
            StockModel stock = new StockModel(1L, 100);
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                stock.update(-1);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고 충분 여부 확인")
    @Nested
    class HasEnough {

        @DisplayName("재고가 충분하면 true를 반환한다")
        @Test
        void returnsTrueWhenEnough() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            // act & assert
            assertThat(stock.hasEnough(10)).isTrue();
        }

        @DisplayName("재고가 부족하면 false를 반환한다")
        @Test
        void returnsFalseWhenNotEnough() {
            // arrange
            StockModel stock = new StockModel(1L, 10);
            // act & assert
            assertThat(stock.hasEnough(11)).isFalse();
        }
    }
}
