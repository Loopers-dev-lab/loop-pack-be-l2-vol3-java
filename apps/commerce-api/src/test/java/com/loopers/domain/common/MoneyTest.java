package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money 값 객체 테스트")
class MoneyTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("양수 금액으로 생성할 수 있다")
        void createWithPositiveAmount() {
            Money money = Money.of(1000L);

            assertThat(money.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @Test
        @DisplayName("0원으로 생성할 수 있다")
        void createWithZero() {
            Money money = Money.zero();

            assertThat(money.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("음수 금액으로 생성 시 예외가 발생한다")
        void createWithNegativeAmountThrowsException() {
            assertThatThrownBy(() -> Money.of(-1000L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("연산 테스트")
    class OperationTest {

        @Test
        @DisplayName("두 금액을 더할 수 있다")
        void addMoney() {
            Money money1 = Money.of(1000L);
            Money money2 = Money.of(500L);

            Money result = money1.add(money2);

            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        }

        @Test
        @DisplayName("금액에 수량을 곱할 수 있다")
        void multiplyMoney() {
            Money money = Money.of(1000L);

            Money result = money.multiply(3);

            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("금액 비교가 가능하다")
        void compareMoney() {
            Money money1 = Money.of(1000L);
            Money money2 = Money.of(500L);

            assertThat(money1.isGreaterThan(money2)).isTrue();
            assertThat(money2.isGreaterThan(money1)).isFalse();
        }
    }

    @Nested
    @DisplayName("동등성 테스트")
    class EqualityTest {

        @Test
        @DisplayName("같은 금액은 동등하다")
        void sameAmountAreEqual() {
            Money money1 = Money.of(1000L);
            Money money2 = Money.of(1000L);

            assertThat(money1).isEqualTo(money2);
        }
    }
}
