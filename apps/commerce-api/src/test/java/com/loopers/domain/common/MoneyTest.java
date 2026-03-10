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

        @Test
        @DisplayName("금액을 뺄 수 있다")
        void subtractMoney() {
            Money money1 = Money.of(1000L);
            Money money2 = Money.of(300L);

            Money result = money1.subtract(money2);

            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(700));
        }

        @Test
        @DisplayName("빼기 결과가 음수이면 예외가 발생한다")
        void subtractMoneyThrowsExceptionWhenNegative() {
            Money money1 = Money.of(300L);
            Money money2 = Money.of(1000L);

            assertThatThrownBy(() -> money1.subtract(money2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("차감 결과가 음수");
        }

        @Test
        @DisplayName("정률 할인 계산이 가능하다")
        void percentageMoney() {
            Money money = Money.of(10000L);

            Money result = money.percentage(10);

            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @Test
        @DisplayName("잘못된 할인율은 예외가 발생한다")
        void percentageWithInvalidRate() {
            Money money = Money.of(10000L);

            assertThatThrownBy(() -> money.percentage(101))
                    .isInstanceOf(CoreException.class);
            assertThatThrownBy(() -> money.percentage(-1))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("BigDecimal 소수 할인율로 정률 할인 계산이 가능하다")
        void percentageWithBigDecimalRate() {
            Money money = Money.of(10000L);

            Money result = money.percentage(new BigDecimal("15.5"));

            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1550));
        }

        @Test
        @DisplayName("BigDecimal 할인율 범위 초과 시 예외가 발생한다")
        void percentageWithInvalidBigDecimalRate() {
            Money money = Money.of(10000L);

            assertThatThrownBy(() -> money.percentage(new BigDecimal("100.1")))
                    .isInstanceOf(CoreException.class);
            assertThatThrownBy(() -> money.percentage(new BigDecimal("-0.1")))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("두 금액 중 작은 값을 반환한다")
        void minMoney() {
            Money money1 = Money.of(1000L);
            Money money2 = Money.of(500L);

            assertThat(money1.min(money2)).isEqualTo(money2);
            assertThat(money2.min(money1)).isEqualTo(money2);
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
