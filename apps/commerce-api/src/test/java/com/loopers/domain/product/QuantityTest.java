package com.loopers.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityTest {

    @DisplayName("Quantity 생성 시")
    @Nested
    class Create {

        @DisplayName("1 이상이면 정상 생성된다.")
        @Test
        void create_withOneOrMore_shouldSucceed() {
            assertThat(new Quantity(1).value()).isEqualTo(1);
            assertThat(new Quantity(10).value()).isEqualTo(10);
        }

        @DisplayName("0이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withZero_shouldFail() {
            assertThrows(IllegalArgumentException.class, () -> new Quantity(0));
        }

        @DisplayName("음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegative_shouldFail() {
            assertThrows(IllegalArgumentException.class, () -> new Quantity(-1));
        }
    }
}
