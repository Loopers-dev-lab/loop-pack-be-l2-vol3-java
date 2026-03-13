package com.loopers.domain.product.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTest {

    @Nested
    @DisplayName("Price 생성")
    class Create {

        @DisplayName("0으로 Price를 생성할 수 있다")
        @Test
        void create_withZero_succeeds() {
            Price price = new Price(0);
            assertThat(price.getValue()).isEqualTo(0);
        }

        @DisplayName("양수로 Price를 생성할 수 있다")
        @Test
        void create_withPositiveValue_succeeds() {
            Price price = new Price(10000);
            assertThat(price.getValue()).isEqualTo(10000);
        }

        @DisplayName("음수로 Price를 생성하면 예외가 발생한다")
        @Test
        void create_withNegativeValue_throwsException() {
            assertThatThrownBy(() -> new Price(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가격은 0 이상이어야 합니다.");
        }
    }
}
