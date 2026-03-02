package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderItemTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_스냅샷이_저장된다() {
            // act
            OrderItem orderItem = OrderItem.create(1L, "에어맥스", "나이키", 150000, 2);

            // assert
            assertThat(orderItem)
                    .extracting(OrderItem::getProductName, OrderItem::getBrandName, OrderItem::getUnitPrice)
                    .containsExactly("에어맥스", "나이키", 150000);
        }

        @Test
        void lineTotal이_unitPrice와_quantity의_곱이다() {
            // act
            OrderItem orderItem = OrderItem.create(1L, "에어맥스", "나이키", 150000, 2);

            // assert
            assertThat(orderItem.getLineTotal()).isEqualTo(300000);
        }
    }
}
