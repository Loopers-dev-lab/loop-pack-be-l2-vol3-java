package com.loopers.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockQuantityTest {

    @DisplayName("StockQuantity 생성 시")
    @Nested
    class Create {

        @DisplayName("0 이상이면 정상 생성된다.")
        @Test
        void create_withZeroOrPositive_shouldSucceed() {
            assertThat(new StockQuantity(0).value()).isEqualTo(0);
            assertThat(new StockQuantity(10).value()).isEqualTo(10);
        }

        @DisplayName("음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegative_shouldFail() {
            assertThrows(IllegalArgumentException.class, () -> new StockQuantity(-1));
        }
    }
}
