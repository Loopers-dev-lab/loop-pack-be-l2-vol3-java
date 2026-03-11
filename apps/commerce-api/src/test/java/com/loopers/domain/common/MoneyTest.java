package com.loopers.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static void assertCoreException(Runnable runnable, String message) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(CoreException.class)
            .hasMessage(message);
    }

    @Nested
    @DisplayName("Money 생성")
    class Create {

        @Test
        @DisplayName("유효한 금액으로 Money 생성에 성공한다")
        void success_create_money() {
            Money money = new Money(1000);

            assertThat(money.value()).isEqualTo(1000);
        }

        @Test
        @DisplayName("0원으로 Money 생성에 성공한다")
        void success_create_money_with_zero() {
            Money money = new Money(0);

            assertThat(money.value()).isEqualTo(0);
        }

        @DisplayName("금액이 null이거나 음수이면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {-1, -1000})
        void fail_when_invalid_amount(Integer value) {
            assertCoreException(
                () -> new Money(value),
                ErrorMessage.Money.AMOUNT_INVALID
            );
        }
    }

    @Nested
    @DisplayName("ZERO 상수")
    class ZeroConstant {

        @Test
        @DisplayName("ZERO는 0원이다")
        void zero_has_value_of_zero() {
            assertThat(Money.ZERO.value()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("금액 더하기")
    class Add {

        @Test
        @DisplayName("두 Money를 더하면 합산된 새 Money를 반환한다")
        void success_add() {
            Money a = new Money(1000);
            Money b = new Money(2000);

            Money result = a.add(b);

            assertThat(result.value()).isEqualTo(3000);
        }

        @Test
        @DisplayName("ZERO와 더하면 원래 금액을 반환한다")
        void add_with_zero_returns_same_value() {
            Money money = new Money(5000);

            Money result = money.add(Money.ZERO);

            assertThat(result.value()).isEqualTo(5000);
        }
    }

    @Nested
    @DisplayName("금액 곱하기")
    class Multiply {

        @Test
        @DisplayName("수량을 곱하면 곱산된 새 Money를 반환한다")
        void success_multiply() {
            Money money = new Money(1000);

            Money result = money.multiply(3);

            assertThat(result.value()).isEqualTo(3000);
        }

        @Test
        @DisplayName("0을 곱하면 ZERO와 같은 금액을 반환한다")
        void multiply_by_zero_returns_zero() {
            Money money = new Money(5000);

            Money result = money.multiply(0);

            assertThat(result.value()).isEqualTo(0);
        }
    }
}
