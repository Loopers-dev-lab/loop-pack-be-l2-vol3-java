package com.loopers.domain.model.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    @DisplayName("유효한 재고 생성 성공")
    void create_success() {
        Stock stock = Stock.of(10);
        assertThat(stock.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 0 생성 성공")
    void create_success_zero() {
        Stock stock = Stock.of(0);
        assertThat(stock.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("음수 재고 생성 시 예외")
    void create_fail_negative() {
        assertThatThrownBy(() -> Stock.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("재고 차감 성공")
    void decrease_success() {
        Stock stock = Stock.of(10);
        Stock decreased = stock.decrease(3);
        assertThat(decreased.getValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 전량 차감 성공")
    void decrease_to_zero() {
        Stock stock = Stock.of(5);
        Stock decreased = stock.decrease(5);
        assertThat(decreased.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 부족 시 차감 예외")
    void decrease_fail_insufficient() {
        Stock stock = Stock.of(3);
        assertThatThrownBy(() -> stock.decrease(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("차감 수량 0 이하면 예외")
    void decrease_fail_zero_quantity() {
        Stock stock = Stock.of(10);
        assertThatThrownBy(() -> stock.decrease(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    @DisplayName("재고 충분 여부 확인")
    void hasEnough() {
        Stock stock = Stock.of(5);
        assertThat(stock.hasEnough(5)).isTrue();
        assertThat(stock.hasEnough(6)).isFalse();
    }
}
