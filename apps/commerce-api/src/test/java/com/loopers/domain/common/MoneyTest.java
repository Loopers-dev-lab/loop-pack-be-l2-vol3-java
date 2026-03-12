package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("Money를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 금액으로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsMoney_whenAmountIsValid() {
            // arrange
            long amount = 10000;

            // act
            Money money = new Money(amount);

            // assert
            assertThat(money.amount()).isEqualTo(10000);
        }

        @DisplayName("금액이 0이면, 정상적으로 생성된다.")
        @Test
        void createsMoney_whenAmountIsZero() {
            // arrange
            long amount = 0;

            // act
            Money money = new Money(amount);

            // assert
            assertThat(money.amount()).isEqualTo(0);
        }

        @DisplayName("금액이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNegative() {
            // arrange
            long amount = -1;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Money(amount);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("금액을 더할 때,")
    @Nested
    class Add {

        @DisplayName("두 금액을 더하면, 합산된 금액이 반환된다.")
        @Test
        void returnsAddedMoney_whenAddingTwoAmounts() {
            // arrange
            Money money1 = new Money(10000);
            Money money2 = new Money(5000);

            // act
            Money result = money1.add(money2);

            // assert
            assertThat(result.amount()).isEqualTo(15000);
        }

        @DisplayName("ZERO에 금액을 더하면, 해당 금액이 반환된다.")
        @Test
        void returnsOriginalMoney_whenAddingToZero() {
            // arrange
            Money money = new Money(10000);

            // act
            Money result = Money.ZERO.add(money);

            // assert
            assertThat(result.amount()).isEqualTo(10000);
        }

        @DisplayName("원본 Money는 변경되지 않는다. (불변성)")
        @Test
        void originalMoneyRemainsUnchanged() {
            // arrange
            Money original = new Money(10000);

            // act
            Money added = original.add(new Money(5000));

            // assert
            assertThat(original.amount()).isEqualTo(10000);
            assertThat(added.amount()).isEqualTo(15000);
        }
    }

    @DisplayName("금액에 수량을 곱할 때,")
    @Nested
    class Multiply {

        @DisplayName("유효한 수량을 곱하면, 곱해진 금액이 반환된다.")
        @Test
        void returnsMultipliedMoney_whenQuantityIsValid() {
            // arrange
            Money money = new Money(10000);
            int quantity = 3;

            // act
            Money result = money.multiply(quantity);

            // assert
            assertThat(result.amount()).isEqualTo(30000);
        }

        @DisplayName("수량이 1이면, 원래 금액이 반환된다.")
        @Test
        void returnsSameMoney_whenQuantityIsOne() {
            // arrange
            Money money = new Money(10000);

            // act
            Money result = money.multiply(1);

            // assert
            assertThat(result.amount()).isEqualTo(10000);
        }

        @DisplayName("수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsZero() {
            // arrange
            Money money = new Money(10000);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                money.multiply(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNegative() {
            // arrange
            Money money = new Money(10000);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                money.multiply(-1);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("원본 Money는 변경되지 않는다. (불변성)")
        @Test
        void originalMoneyRemainsUnchanged() {
            // arrange
            Money original = new Money(10000);

            // act
            Money multiplied = original.multiply(3);

            // assert
            assertThat(original.amount()).isEqualTo(10000);
            assertThat(multiplied.amount()).isEqualTo(30000);
        }
    }

    @DisplayName("ZERO 상수는,")
    @Nested
    class ZeroConstant {

        @DisplayName("금액이 0인 Money이다.")
        @Test
        void hasZeroAmount() {
            // assert
            assertThat(Money.ZERO.amount()).isEqualTo(0);
        }
    }
}