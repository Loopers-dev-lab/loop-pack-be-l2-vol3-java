package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order 상태 전이 단위 테스트")
class OrderStatusTransitionTest {

    private Order createTestOrder() {
        List<OrderItem> orderItems = List.of(
                OrderItem.create(1L, "상품A", new BigDecimal("10000"), 1)
        );
        return Order.create(1L, orderItems, BigDecimal.ZERO, null);
    }

    @Nested
    @DisplayName("startPayment - 결제 시작")
    class StartPayment {

        @Test
        @DisplayName("성공: CREATED 상태에서 PAYMENT_PENDING으로 전이한다")
        void startPayment_fromCreated_success() {
            // Given
            Order order = createTestOrder();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

            // When
            order.startPayment();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }

        @Test
        @DisplayName("실패: PAYMENT_PENDING 상태에서는 결제를 시작할 수 없다")
        void startPayment_fromPaymentPending_fail() {
            // Given
            Order order = createTestOrder();
            order.startPayment();

            // When & Then
            assertThatThrownBy(order::startPayment)
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: PAID 상태에서는 결제를 시작할 수 없다")
        void startPayment_fromPaid_fail() {
            // Given
            Order order = createTestOrder();
            order.startPayment();
            order.completePayment();

            // When & Then
            assertThatThrownBy(order::startPayment)
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("completePayment - 결제 완료")
    class CompletePayment {

        @Test
        @DisplayName("성공: PAYMENT_PENDING 상태에서 PAID로 전이한다")
        void completePayment_fromPaymentPending_success() {
            // Given
            Order order = createTestOrder();
            order.startPayment();

            // When
            order.completePayment();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("실패: CREATED 상태에서는 결제를 완료할 수 없다")
        void completePayment_fromCreated_fail() {
            // Given
            Order order = createTestOrder();

            // When & Then
            assertThatThrownBy(order::completePayment)
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("failPayment - 결제 실패")
    class FailPayment {

        @Test
        @DisplayName("성공: PAYMENT_PENDING 상태에서 PAYMENT_FAILED로 전이한다")
        void failPayment_fromPaymentPending_success() {
            // Given
            Order order = createTestOrder();
            order.startPayment();

            // When
            order.failPayment();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("실패: CREATED 상태에서는 결제 실패 처리할 수 없다")
        void failPayment_fromCreated_fail() {
            // Given
            Order order = createTestOrder();

            // When & Then
            assertThatThrownBy(order::failPayment)
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}
