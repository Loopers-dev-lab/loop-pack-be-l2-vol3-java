package com.loopers.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class MoneyTest {

    @DisplayName("Money를 생성할 때,")
    @Nested
    class Wons {

        @DisplayName("유효한 금액이면, 정상 생성된다.")
        @ParameterizedTest(name = "금액이 {0}인 경우")
        @ValueSource(longs = {0L, 10000L})
        void success(Long amount) {
            // act
            var money = Money.wons(amount);

            // assert
            assertThat(money.getAmount()).isEqualTo(amount);
        }

        @DisplayName("금액이 null이면, REQUIRED_MONEY_AMOUNT 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenNull(Long amount) {
            assertThatThrownBy(() -> Money.wons(amount))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_MONEY_AMOUNT.getMessage());
        }

        @DisplayName("금액이 음수이면, INVALID_MONEY_AMOUNT 에러가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, -999L})
        void throwsException_whenNegative(Long amount) {
            assertThatThrownBy(() -> Money.wons(amount))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_MONEY_AMOUNT.getMessage());
        }
    }

    @DisplayName("nullable Money를 생성할 때,")
    @Nested
    class WonsOrNull {

        @DisplayName("금액이 null이면, NPE 없이 null을 반환한다.")
        @Test
        void returnsNull_whenAmountIsNull() {
            // act
            var money = Money.wonsOrNull(null);

            // assert
            assertThat(money).isNull();
        }

        @DisplayName("유효한 금액이면, Money를 정상 생성한다.")
        @Test
        void returnsMoney_whenAmountIsValid() {
            // act
            var money = Money.wonsOrNull(10000L);

            // assert
            assertThat(money).isEqualTo(Money.wons(10000L));
        }

        @DisplayName("금액이 음수이면, INVALID_MONEY_AMOUNT 에러가 발생한다.")
        @Test
        void throwsException_whenAmountIsNegative() {
            assertThatThrownBy(() -> Money.wonsOrNull(-1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_MONEY_AMOUNT.getMessage());
        }
    }

    @DisplayName("금액을 더할 때,")
    @Nested
    class Plus {

        @DisplayName("두 금액의 합을 반환한다.")
        @Test
        void returnsSumOfTwoAmounts() {
            // arrange
            var money1 = Money.wons(10000L);
            var money2 = Money.wons(5000L);

            // act
            var result = money1.plus(money2);

            // assert
            assertThat(result).isEqualTo(Money.wons(15000L));
        }
    }

    @DisplayName("금액을 뺄 때,")
    @Nested
    class Minus {

        @DisplayName("차액을 반환한다.")
        @Test
        void returnsDifference() {
            // arrange
            var money1 = Money.wons(10000L);
            var money2 = Money.wons(3000L);

            // act
            var result = money1.minus(money2);

            // assert
            assertThat(result).isEqualTo(Money.wons(7000L));
        }

        @DisplayName("결과가 음수이면, INVALID_MONEY_AMOUNT 에러가 발생한다.")
        @Test
        void throwsException_whenResultIsNegative() {
            // arrange
            var money1 = Money.wons(3000L);
            var money2 = Money.wons(5000L);

            // act & assert
            assertThatThrownBy(() -> money1.minus(money2))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_MONEY_AMOUNT.getMessage());
        }
    }

    @DisplayName("금액을 곱할 때,")
    @Nested
    class Multiply {

        @DisplayName("금액과 배수의 곱을 반환한다.")
        @Test
        void returnsProductOfAmountAndMultiplier() {
            // arrange
            var money = Money.wons(10000L);

            // act
            var result = money.multiply(3L);

            // assert
            assertThat(result).isEqualTo(Money.wons(30000L));
        }
    }

    @DisplayName("금액을 비교할 때,")
    @Nested
    class IsLessThan {

        @DisplayName("자신이 더 작으면, true를 반환한다.")
        @Test
        void returnsTrue_whenLessThan() {
            // arrange
            var money1 = Money.wons(3000L);
            var money2 = Money.wons(5000L);

            // act & assert
            assertThat(money1.isLessThan(money2)).isTrue();
        }

        @DisplayName("자신이 더 크면, false를 반환한다.")
        @Test
        void returnsFalse_whenGreaterThan() {
            // arrange
            var money1 = Money.wons(5000L);
            var money2 = Money.wons(3000L);

            // act & assert
            assertThat(money1.isLessThan(money2)).isFalse();
        }

        @DisplayName("두 금액이 같으면, false를 반환한다.")
        @Test
        void returnsFalse_whenEqual() {
            // arrange
            var money1 = Money.wons(5000L);
            var money2 = Money.wons(5000L);

            // act & assert
            assertThat(money1.isLessThan(money2)).isFalse();
        }
    }

    @DisplayName("두 금액 중 작은 값을 구할 때,")
    @Nested
    class Min {

        @DisplayName("첫 번째 금액이 작으면, 첫 번째 금액을 반환한다.")
        @Test
        void returnsFirst_whenFirstIsSmaller() {
            // arrange
            var money1 = Money.wons(3000L);
            var money2 = Money.wons(5000L);

            // act
            var result = Money.min(money1, money2);

            // assert
            assertThat(result).isEqualTo(Money.wons(3000L));
        }

        @DisplayName("두 번째 금액이 작으면, 두 번째 금액을 반환한다.")
        @Test
        void returnsSecond_whenSecondIsSmaller() {
            // arrange
            var money1 = Money.wons(5000L);
            var money2 = Money.wons(3000L);

            // act
            var result = Money.min(money1, money2);

            // assert
            assertThat(result).isEqualTo(Money.wons(3000L));
        }

        @DisplayName("두 금액이 같으면, 동일한 금액을 반환한다.")
        @Test
        void returnsSameAmount_whenEqual() {
            // arrange
            var money1 = Money.wons(5000L);
            var money2 = Money.wons(5000L);

            // act
            var result = Money.min(money1, money2);

            // assert
            assertThat(result).isEqualTo(Money.wons(5000L));
        }
    }
}