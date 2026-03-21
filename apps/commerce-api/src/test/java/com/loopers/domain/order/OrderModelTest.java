package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderModel Entity")
class OrderModelTest {

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @Test
        @DisplayName("create() 정적 팩토리로 주문 생성 성공")
        void create_order_success() {
            // given
            Long memberId = 1L;
            OrderItemModel item1 = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 2);
            OrderItemModel item2 = OrderItemModel.create("prod2", "Product 2", new BigDecimal("20000"), 1);
            List<OrderItemModel> items = List.of(item1, item2);

            // when
            OrderModel order = OrderModel.create(memberId, items);

            // then
            assertThat(order).isNotNull();
            assertThat(order.getOrderId()).isNotNull();
            assertThat(order.getRefMemberId().value()).isEqualTo(memberId);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getOrderItems()).hasSize(2);
        }

        @Test
        @DisplayName("주문 상품이 비어있으면 예외 발생")
        void create_emptyItems_throwsException() {
            // given
            Long memberId = 1L;
            List<OrderItemModel> items = List.of();

            // when & then
            assertThatThrownBy(() -> OrderModel.create(memberId, items))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("주문 상품이 비어 있습니다");
        }

        @Test
        @DisplayName("총 주문 금액 계산")
        void getTotalAmount() {
            // given
            Long memberId = 1L;
            OrderItemModel item1 = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 2); // 20000
            OrderItemModel item2 = OrderItemModel.create("prod2", "Product 2", new BigDecimal("20000"), 1); // 20000
            OrderModel order = OrderModel.create(memberId, List.of(item1, item2));

            // when
            BigDecimal totalAmount = order.getFinalAmount();

            // then
            assertThat(totalAmount).isEqualByComparingTo(new BigDecimal("40000"));
        }
    }

    @DisplayName("결제를 완료할 때,")
    @Nested
    class Pay {

        @Test
        @DisplayName("PENDING 상태에서 결제 완료 성공")
        void pay_fromPending_success() {
            // given
            Long memberId = 1L;
            OrderItemModel item = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 1);
            OrderModel order = OrderModel.create(memberId, List.of(item));

            // when
            order.pay();

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("PAID 상태에서 다시 결제 시도 시 예외 발생")
        void pay_fromPaid_throwsException() {
            // given
            Long memberId = 1L;
            OrderItemModel item = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 1);
            OrderModel order = OrderModel.create(memberId, List.of(item));
            order.pay();

            // when & then
            assertThatThrownBy(order::pay)
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("CANCELED 상태에서 결제 시도 시 예외 발생")
        void pay_fromCanceled_throwsException() {
            // given
            Long memberId = 1L;
            OrderItemModel item = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 1);
            OrderModel order = OrderModel.create(memberId, List.of(item));
            order.cancel();

            // when & then
            assertThatThrownBy(order::pay)
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class Cancel {

        @Test
        @DisplayName("PENDING 상태에서 취소 성공")
        void cancel_fromPending_success() {
            // given
            Long memberId = 1L;
            OrderItemModel item = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 1);
            OrderModel order = OrderModel.create(memberId, List.of(item));

            // when
            order.cancel();

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("이미 취소된 주문 재취소 시 멱등성 보장")
        void cancel_alreadyCanceled_idempotent() {
            // given
            Long memberId = 1L;
            OrderItemModel item = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 1);
            OrderModel order = OrderModel.create(memberId, List.of(item));
            order.cancel();

            // when
            order.cancel(); // 두 번째 취소

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }
    }

    @DisplayName("주문 소유자 확인")
    @Nested
    class IsOwner {

        @Test
        @DisplayName("소유자가 맞으면 true 반환")
        void isOwner_correctMember_returnsTrue() {
            // given
            Long memberId = 1L;
            OrderItemModel item = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 1);
            OrderModel order = OrderModel.create(memberId, List.of(item));

            // when
            boolean isOwner = order.isOwner(1L);

            // then
            assertThat(isOwner).isTrue();
        }

        @Test
        @DisplayName("소유자가 아니면 false 반환")
        void isOwner_wrongMember_returnsFalse() {
            // given
            Long memberId = 1L;
            OrderItemModel item = OrderItemModel.create("prod1", "Product 1", new BigDecimal("10000"), 1);
            OrderModel order = OrderModel.create(memberId, List.of(item));

            // when
            boolean isOwner = order.isOwner(2L);

            // then
            assertThat(isOwner).isFalse();
        }
    }
}
