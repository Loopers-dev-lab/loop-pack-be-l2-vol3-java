package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Nested
    @DisplayName("Stock 생성")
    class Create {

        @DisplayName("0 이상의 수량으로 Stock을 생성할 수 있다")
        @Test
        void create_withValidQuantity_succeeds() {
            Stock stock = new Stock(10);
            assertThat(stock.getQuantity()).isEqualTo(10);
        }

        @DisplayName("음수로 Stock을 생성하면 예외가 발생한다")
        @Test
        void create_withNegativeQuantity_throwsException() {
            assertThatThrownBy(() -> new Stock(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재고는 0 이상이어야 합니다.");
        }
    }

    @Nested
    @DisplayName("hasEnough")
    class HasEnough {

        @DisplayName("재고가 요청 수량 이상이면 true를 반환한다")
        @Test
        void hasEnough_withSufficientStock_returnsTrue() {
            Stock stock = new Stock(10);
            assertThat(stock.hasEnough(10)).isTrue();
        }

        @DisplayName("재고가 요청 수량 미만이면 false를 반환한다")
        @Test
        void hasEnough_withInsufficientStock_returnsFalse() {
            Stock stock = new Stock(5);
            assertThat(stock.hasEnough(6)).isFalse();
        }
    }

    @Nested
    @DisplayName("decrease")
    class Decrease {

        @DisplayName("재고가 충분하면 차감된 Stock을 반환한다")
        @Test
        void decrease_withSufficientStock_returnsDecreasedStock() {
            Stock stock = new Stock(10);
            Stock decreased = stock.decrease(3);
            assertThat(decreased.getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면 CoreException이 발생한다")
        @Test
        void decrease_withInsufficientStock_throwsException() {
            Stock stock = new Stock(2);
            assertThatThrownBy(() -> stock.decrease(3))
                .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("increase")
    class Increase {

        @DisplayName("수량을 증가시킨 Stock을 반환한다")
        @Test
        void increase_withValidAmount_returnsIncreasedStock() {
            Stock stock = new Stock(5);
            Stock increased = stock.increase(3);
            assertThat(increased.getQuantity()).isEqualTo(8);
        }
    }
}
