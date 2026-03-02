package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemModelTest {

    private static final ProductSnapshot SNAPSHOT = new ProductSnapshot(1L, "테스트 상품",
            Money.of(new BigDecimal("10000")));
    private static final int QUANTITY = 2;
    private static final Long OPTION_ID = 10L;

    @DisplayName("of 시")
    @Nested
    class Of {

        @DisplayName("유효한 스냅샷과 수량이 주어지면 생성된다.")
        @Test
        void of_withValidInputs_shouldSucceed() {
            // when
            OrderItemModel item = OrderItemModel.of(SNAPSHOT, Quantity.of(QUANTITY), OPTION_ID);

            // then
            assertThat(item.getProductId()).isEqualTo(SNAPSHOT.productId());
            assertThat(item.getProductNameSnapshot()).isEqualTo(SNAPSHOT.productName());
            assertThat(item.getPriceSnapshot()).isEqualByComparingTo(SNAPSHOT.price().value());
            assertThat(item.getQuantity()).isEqualTo(QUANTITY);
            assertThat(item.getOptionId()).isEqualTo(OPTION_ID);
        }

        @DisplayName("optionId가 null이어도 생성된다.")
        @Test
        void of_withNullOptionId_shouldSucceed() {
            OrderItemModel item = OrderItemModel.of(SNAPSHOT, Quantity.of(QUANTITY), null);
            assertThat(item.getOptionId()).isNull();
        }

        @DisplayName("스냅샷이 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void of_withNullSnapshot_shouldThrow() {
            assertThrows(IllegalArgumentException.class,
                    () -> OrderItemModel.of(null, Quantity.of(QUANTITY), OPTION_ID));
        }

        @DisplayName("수량이 0이면 IllegalArgumentException이 발생한다.")
        @Test
        void of_withZeroQuantity_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> OrderItemModel.of(SNAPSHOT, Quantity.of(0), OPTION_ID));
        }

        @DisplayName("수량이 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void of_withNegativeQuantity_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> OrderItemModel.of(SNAPSHOT, Quantity.of(-1), OPTION_ID));
        }
    }
}
