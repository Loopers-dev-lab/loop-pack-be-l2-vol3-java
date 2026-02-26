package com.loopers.domain.model.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("유효한 금액 생성 성공")
    void create_success() {
        Money money = Money.of(10000);
        assertThat(money.getValue()).isEqualTo(10000);
    }

    @Test
    @DisplayName("0원 생성 성공")
    void create_success_zero() {
        Money money = Money.of(0);
        assertThat(money.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("음수 금액 생성 시 예외")
    void create_fail_negative() {
        assertThatThrownBy(() -> Money.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("금액 덧셈")
    void add() {
        Money a = Money.of(1000);
        Money b = Money.of(2000);
        assertThat(a.add(b).getValue()).isEqualTo(3000);
    }

    @Test
    @DisplayName("금액 뺄셈")
    void subtract() {
        Money a = Money.of(3000);
        Money b = Money.of(1000);
        assertThat(a.subtract(b).getValue()).isEqualTo(2000);
    }

    @Test
    @DisplayName("금액 뺄셈 결과 음수면 예외")
    void subtract_fail_negative_result() {
        Money a = Money.of(1000);
        Money b = Money.of(2000);
        assertThatThrownBy(() -> a.subtract(b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("음수");
    }

    @Test
    @DisplayName("금액 곱셈")
    void multiply() {
        Money money = Money.of(5000);
        assertThat(money.multiply(3).getValue()).isEqualTo(15000);
    }

    @Test
    @DisplayName("금액 곱셈 음수 수량이면 예외")
    void multiply_fail_negative() {
        Money money = Money.of(5000);
        assertThatThrownBy(() -> money.multiply(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("zero 팩토리 메서드")
    void zero() {
        assertThat(Money.zero().getValue()).isEqualTo(0);
    }
}
