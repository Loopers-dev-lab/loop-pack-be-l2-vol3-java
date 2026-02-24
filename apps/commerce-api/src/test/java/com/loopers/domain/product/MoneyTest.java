package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("Money를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0 이상의 금액이면, 정상 생성된다.")
        @Test
        void createsMoney_whenAmountIsZeroOrPositive() {
            Money money = new Money(1000);
            assertThat(money.amount()).isEqualTo(1000);
        }

        @DisplayName("0원이면, 정상 생성된다.")
        @Test
        void createsMoney_whenAmountIsZero() {
            Money money = new Money(0);
            assertThat(money.amount()).isEqualTo(0);
        }

        @DisplayName("음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsNegative() {
            CoreException result = assertThrows(CoreException.class, () -> new Money(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 연산할 때, ")
    @Nested
    class Operations {

        @DisplayName("더하면, 합산된 금액을 반환한다.")
        @Test
        void returnsSum_whenAdding() {
            Money a = new Money(1000);
            Money b = new Money(2000);
            assertThat(a.plus(b)).isEqualTo(new Money(3000));
        }

        @DisplayName("빼면, 차감된 금액을 반환한다.")
        @Test
        void returnsDifference_whenSubtracting() {
            Money a = new Money(3000);
            Money b = new Money(1000);
            assertThat(a.minus(b)).isEqualTo(new Money(2000));
        }

        @DisplayName("빼서 음수가 되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMinusResultsInNegative() {
            Money a = new Money(1000);
            Money b = new Money(3000);
            CoreException result = assertThrows(CoreException.class, () -> a.minus(b));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("곱하면, 곱셈된 금액을 반환한다.")
        @Test
        void returnsProduct_whenMultiplying() {
            Money money = new Money(1000);
            assertThat(money.multiply(3)).isEqualTo(new Money(3000));
        }

        @DisplayName("크거나 같은지 비교할 수 있다.")
        @Test
        void comparesGreaterThanOrEqual() {
            Money a = new Money(3000);
            Money b = new Money(2000);
            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
            assertThat(b.isGreaterThanOrEqual(a)).isFalse();
            assertThat(a.isGreaterThanOrEqual(new Money(3000))).isTrue();
        }
    }
}
