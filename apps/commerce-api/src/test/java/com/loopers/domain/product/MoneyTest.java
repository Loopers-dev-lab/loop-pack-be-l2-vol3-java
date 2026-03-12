package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("Money 생성")
    @Nested
    class Create {

        @DisplayName("0원으로 생성할 수 있다")
        @Test
        void createsWithZero() {
            // arrange & act
            Money money = new Money(0);
            // assert
            assertThat(money.value()).isEqualTo(0);
        }

        @DisplayName("양수 금액으로 생성할 수 있다")
        @Test
        void createsWithPositiveValue() {
            // arrange & act
            Money money = new Money(10000);
            // assert
            assertThat(money.value()).isEqualTo(10000);
        }

        @DisplayName("음수 금액이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNegativeValue() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Money(-1);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Money 연산")
    @Nested
    class Operations {

        @DisplayName("multiply: 금액에 수량을 곱한 새 Money를 반환한다")
        @Test
        void multipliesValue() {
            // arrange
            Money money = new Money(1000);
            // act
            Money result = money.multiply(3);
            // assert
            assertAll(
                () -> assertThat(result.value()).isEqualTo(3000),
                () -> assertThat(result).isNotSameAs(money)
            );
        }

        @DisplayName("add: 두 Money를 더한 새 Money를 반환한다")
        @Test
        void addsValues() {
            // arrange
            Money a = new Money(1000);
            Money b = new Money(2000);
            // act
            Money result = a.add(b);
            // assert
            assertAll(
                () -> assertThat(result.value()).isEqualTo(3000),
                () -> assertThat(result).isNotSameAs(a)
            );
        }
    }

    @DisplayName("Money 동등성")
    @Nested
    class Equality {

        @DisplayName("같은 금액의 Money는 동등하다")
        @Test
        void equalForSameValue() {
            // arrange
            Money a = new Money(1000);
            Money b = new Money(1000);
            // assert
            assertAll(
                () -> assertThat(a).isEqualTo(b),
                () -> assertThat(a.hashCode()).isEqualTo(b.hashCode())
            );
        }

        @DisplayName("다른 금액의 Money는 동등하지 않다")
        @Test
        void notEqualForDifferentValue() {
            // arrange
            Money a = new Money(1000);
            Money b = new Money(2000);
            // assert
            assertThat(a).isNotEqualTo(b);
        }
    }
}
