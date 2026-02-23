package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OrderItemTest {

    @DisplayName("주문 항목을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("수량이 0이면, INVALID_ORDER_ITEM_QUANTITY 예외가 발생한다.")
        @Test
        void throwsException_whenQuantityIsZero() {
            assertThatThrownBy(() -> OrderItem.create(1L, "상품", "https://thumb.png", Money.wons(10000L), 0L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_ORDER_ITEM_QUANTITY.getMessage());
        }

        @DisplayName("수량이 음수이면, INVALID_ORDER_ITEM_QUANTITY 예외가 발생한다.")
        @Test
        void throwsException_whenQuantityIsNegative() {
            assertThatThrownBy(() -> OrderItem.create(1L, "상품", "https://thumb.png", Money.wons(10000L), -1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_ORDER_ITEM_QUANTITY.getMessage());
        }
    }

    @DisplayName("소계를 계산할 때,")
    @Nested
    class CalculateSubtotal {

        @DisplayName("가격 × 수량을 반환한다.")
        @Test
        void returnsProductPriceMultipliedByQuantity() {
            // arrange
            var item = OrderItem.create(1L, "상품", "https://thumb.png", Money.wons(10000L), 3L);

            // act
            var subtotal = item.calculateSubtotal();

            // assert
            assertThat(subtotal).isEqualTo(Money.wons(30000L));
        }
    }
}