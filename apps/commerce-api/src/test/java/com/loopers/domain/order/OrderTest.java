package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.OrderErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderTest {

    private Order createPendingOrder() {
        List<OrderItem> items = List.of(
                OrderItem.create(1L, "에어맥스", "나이키", 150000, 2),
                OrderItem.create(2L, "슈퍼스타", "아디다스", 120000, 1)
        );
        return Order.create(1L, "ORD-20260222-001", items,
                "홍길동", "010-1234-5678",
                "김철수", "010-9876-5432",
                "06234", "서울시 강남구 테헤란로 123", "4층 401호");
    }

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_PENDING_상태로_생성된다() {
            // act
            Order order = createPendingOrder();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        void subtotalAmount가_OrderItem의_lineTotal_합산이다() {
            // act
            Order order = createPendingOrder();

            // assert — 150000*2 + 120000*1 = 420000
            assertThat(order.getSubtotalAmount()).isEqualTo(420000);
        }

        @Test
        void totalAmount가_subtotalAmount와_동일하다() {
            // act
            Order order = createPendingOrder();

            // assert
            assertThat(order.getTotalAmount()).isEqualTo(420000);
        }

        @Test
        void expiresAt이_설정된다() {
            // act
            Order order = createPendingOrder();

            // assert
            assertThat(order.getExpiresAt()).isNotNull();
        }

        @Test
        void orderNumber가_설정된다() {
            // act
            Order order = createPendingOrder();

            // assert
            assertThat(order.getOrderNumber()).isEqualTo("ORD-20260222-001");
        }

        @Test
        void 배송지_스냅샷이_저장된다() {
            // act
            Order order = createPendingOrder();

            // assert
            assertThat(order)
                    .extracting(Order::getReceiverName, Order::getReceiverPhone,
                            Order::getZipCode, Order::getAddressLine1)
                    .containsExactly("김철수", "010-9876-5432", "06234", "서울시 강남구 테헤란로 123");
        }
    }

    @DisplayName("결제 확정할 때,")
    @Nested
    class 결제확정 {

        @Test
        void PENDING이_아니면_예외가_발생한다() {
            // arrange
            Order order = createPendingOrder();
            order.cancel();

            // act & assert
            assertThatThrownBy(() -> order.confirm(1L, "CARD"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.INVALID_ORDER_STATUS);
        }

        @Test
        void PENDING이면_PAID로_전이된다() {
            // arrange
            Order order = createPendingOrder();

            // act
            order.confirm(100L, "CARD");

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        void paymentId가_설정된다() {
            // arrange
            Order order = createPendingOrder();

            // act
            order.confirm(100L, "CARD");

            // assert
            assertThat(order.getPaymentId()).isEqualTo(100L);
        }

        @Test
        void orderedAt이_설정된다() {
            // arrange
            Order order = createPendingOrder();

            // act
            order.confirm(100L, "CARD");

            // assert
            assertThat(order.getOrderedAt()).isNotNull();
        }

        @Test
        void paymentMethod가_설정된다() {
            // arrange
            Order order = createPendingOrder();

            // act
            order.confirm(100L, "CARD");

            // assert
            assertThat(order.getPaymentMethod()).isEqualTo("CARD");
        }
    }

    @DisplayName("취소할 때,")
    @Nested
    class 취소 {

        @Test
        void PENDING이_아니면_예외가_발생한다() {
            // arrange
            Order order = createPendingOrder();
            order.confirm(1L, "CARD");

            // act & assert
            assertThatThrownBy(order::cancel)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.INVALID_ORDER_STATUS);
        }

        @Test
        void PENDING이면_CANCELED로_전이된다() {
            // arrange
            Order order = createPendingOrder();

            // act
            order.cancel();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        void canceledAt이_설정된다() {
            // arrange
            Order order = createPendingOrder();

            // act
            order.cancel();

            // assert
            assertThat(order.getCanceledAt()).isNotNull();
        }
    }

    @DisplayName("만료할 때,")
    @Nested
    class 만료 {

        @Test
        void PENDING이_아니면_예외가_발생한다() {
            // arrange
            Order order = createPendingOrder();
            order.confirm(1L, "CARD");

            // act & assert
            assertThatThrownBy(order::expire)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.INVALID_ORDER_STATUS);
        }

        @Test
        void PENDING이면_EXPIRED로_전이된다() {
            // arrange
            Order order = createPendingOrder();

            // act
            order.expire();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        }
    }

    @DisplayName("할인을 적용할 때,")
    @Nested
    class 할인적용 {

        @Test
        void PENDING이_아니면_예외가_발생한다() {
            // arrange
            Order order = createPendingOrder();
            order.confirm(1L, "CARD");

            // act & assert
            assertThatThrownBy(() -> order.applyDiscount(10000, 5000, 3000, null))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.INVALID_ORDER_STATUS);
        }

        @Test
        void totalAmount가_재계산된다() {
            // arrange
            Order order = createPendingOrder();

            // act — subtotal(420000) - discount(10000) - point(5000) + shipping(3000) = 408000
            order.applyDiscount(10000, 5000, 3000, null);

            // assert
            assertThat(order.getTotalAmount()).isEqualTo(408000);
        }
    }

    @DisplayName("소유권을 확인할 때,")
    @Nested
    class 소유권확인 {

        @Test
        void 본인_주문이_아니면_예외가_발생한다() {
            // arrange
            Order order = createPendingOrder();

            // act & assert
            assertThatThrownBy(() -> order.validateOwnership(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.NOT_OWNER);
        }

        @Test
        void 본인_주문이면_예외가_발생하지_않는다() {
            // arrange
            Order order = createPendingOrder();

            // act & assert
            assertThatCode(() -> order.validateOwnership(1L)).doesNotThrowAnyException();
        }
    }
}
