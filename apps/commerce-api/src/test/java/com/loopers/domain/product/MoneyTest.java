package com.loopers.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @DisplayName("Money 생성 시")
    @Nested
    class Create {

        @DisplayName("null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNull_shouldFail() {
            assertThrows(IllegalArgumentException.class, () -> new Money(null));
        }

        @DisplayName("0 이상이면 정상 생성된다.")
        @Test
        void create_withZeroOrPositive_shouldSucceed() {
            assertThat(new Money(BigDecimal.ZERO).value()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(new Money(new BigDecimal("100.50")).value()).isEqualByComparingTo(new BigDecimal("100.50"));
        }

        @DisplayName("음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegative_shouldFail() {
            assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-1")));
        }
    }
}
