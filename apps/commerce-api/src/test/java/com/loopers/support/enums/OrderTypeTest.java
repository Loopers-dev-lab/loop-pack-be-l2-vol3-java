package com.loopers.support.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderType 열거형 테스트")
class OrderTypeTest {

    @Test
    @DisplayName("DIRECT, CART 값이 존재한다")
    void values_ShouldContain_DIRECT_CART() {
        assertThat(OrderType.values())
                .containsExactlyInAnyOrder(
                        OrderType.DIRECT,
                        OrderType.CART
                );
    }
}
