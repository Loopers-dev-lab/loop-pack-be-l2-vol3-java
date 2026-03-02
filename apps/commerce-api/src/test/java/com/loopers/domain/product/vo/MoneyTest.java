package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MoneyTest {

    @DisplayName("Money를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 정상 생성된다")
        @Test
        void success() {
            Money money = assertDoesNotThrow(() -> new Money(1000));
            assertThat(money.value()).isEqualTo(1000);
        }

        @DisplayName("0이면 예외가 발생한다")
        @Test
        void throwsException_whenZero() {
            assertThatThrownBy(() -> new Money(0))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("음수이면 예외가 발생한다")
        @Test
        void throwsException_whenNegative() {
            assertThatThrownBy(() -> new Money(-1000))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("Money를 곱할 때, ")
    @Nested
    class Multiply {

        @DisplayName("수량을 곱하면 곱한 금액이 반환된다")
        @Test
        void success() {
            Money money = new Money(1000);
            Money result = money.multiply(3);
            assertThat(result.value()).isEqualTo(3000);
        }
    }

    @DisplayName("Money를 더할 때, ")
    @Nested
    class Add {

        @DisplayName("다른 Money와 더하면 합산된 금액이 반환된다")
        @Test
        void success() {
            Money money = new Money(1000);
            Money result = money.add(new Money(2000));
            assertThat(result.value()).isEqualTo(3000);
        }
    }
}
