package com.loopers.domain.order;

import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.StockQuantity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    private static final Long USER_ID = 1L;
    private static final ProductSnapshot SNAPSHOT = new ProductSnapshot(100L, "상품", Money.of(new BigDecimal("5000")));

    @DisplayName("create 시")
    @Nested
    class Create {

        @DisplayName("유효한 userId가 주어지면 ORDERED 상태로 생성된다.")
        @Test
        void create_withValidUserId_shouldSucceed() {
            // when
            OrderModel order = OrderModel.create(USER_ID);

            // then
            assertThat(order.getUserId()).isEqualTo(USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(order.getOrderedAt()).isNotNull();
            assertThat(order.getOrderItems()).isEmpty();
        }

        @DisplayName("userId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullUserId_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> OrderModel.create(null));
        }
    }

    @DisplayName("addItem 시")
    @Nested
    class AddItem {

        @DisplayName("유효한 항목을 추가할 수 있다.")
        @Test
        void addItem_withValidItem_shouldAdd() {
            // given
            OrderModel order = OrderModel.create(USER_ID);
            OrderItemModel item = OrderItemModel.of(SNAPSHOT, Quantity.of(2), null);

            // when
            order.addItem(item);

            // then
            assertThat(order.getOrderItems()).hasSize(1);
            assertThat(order.getOrderItems().get(0).getProductId()).isEqualTo(SNAPSHOT.productId());
        }

        @DisplayName("항목이 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void addItem_withNullItem_shouldThrow() {
            // given
            OrderModel order = OrderModel.create(USER_ID);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> order.addItem(null));
        }
    }

    @DisplayName("validateHasItems 시")
    @Nested
    class ValidateHasItems {

        @DisplayName("항목이 없으면 IllegalArgumentException이 발생한다.")
        @Test
        void validateHasItems_whenEmpty_shouldThrow() {
            // given
            OrderModel order = OrderModel.create(USER_ID);

            // when & then
            assertThrows(IllegalArgumentException.class, order::validateHasItems);
        }

        @DisplayName("항목이 1개 이상이면 통과한다.")
        @Test
        void validateHasItems_whenHasItems_shouldPass() {
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
            order.validateHasItems();
        }
    }

    @DisplayName("markPaid 시")
    @Nested
    class MarkPaid {

        @DisplayName("ORDERED가 아니면 IllegalStateException이 발생한다.")
        @Test
        void markPaid_whenNotOrdered_shouldThrowIllegalStateException() {
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
            order.cancel();
            assertThrows(IllegalStateException.class, order::markPaid);
        }
    }

    @DisplayName("canCancel 시")
    @Nested
    class CanCancel {

        @DisplayName("ORDERED 상태면 true를 반환한다.")
        @Test
        void canCancel_whenOrdered_shouldReturnTrue() {
            // given
            OrderModel order = OrderModel.create(USER_ID);

            // when
            boolean result = order.canCancel();

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("CANCELLED 상태면 false를 반환한다.")
        @Test
        void canCancel_whenCancelled_shouldReturnFalse() {
            // given
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
            order.cancel();

            // when
            boolean result = order.canCancel();

            // then
            assertThat(result).isFalse();
        }
    }

    @DisplayName("cancel 시")
    @Nested
    class Cancel {

        @DisplayName("ORDERED 상태면 CANCELLED로 변경된다.")
        @Test
        void cancel_whenOrdered_shouldChangeToCancelled() {
            // given
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));

            // when
            order.cancel();

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 CANCELLED 상태면 IllegalStateException이 발생한다.")
        @Test
        void cancel_whenAlreadyCancelled_shouldThrow() {
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
            order.cancel();
            assertThrows(IllegalStateException.class, order::cancel);
        }
    }
}
