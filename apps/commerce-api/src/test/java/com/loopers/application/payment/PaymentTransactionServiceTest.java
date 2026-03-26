package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderHistoryService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentTransactionService 단위 테스트")
class PaymentTransactionServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderHistoryService orderHistoryService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentTransactionService paymentTransactionService;

    @Nested
    @DisplayName("failPayment - 결제 실패 처리")
    class FailPayment {

        @Test
        @DisplayName("성공: 결제 실패 시 Payment와 Order 상태를 변경한다")
        void failPayment_updatesStatus() {
            // Given
            Long orderId = 1L;
            Payment payment = Payment.create(orderId, 1L, new BigDecimal("50000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            List<OrderItem> orderItems = List.of(
                    OrderItem.create(1L, "상품A", new BigDecimal("50000"), 1)
            );
            Order order = Order.create(1L, orderItems, BigDecimal.ZERO, null);
            order.startPayment();

            given(orderService.getById(orderId)).willReturn(order);

            // When
            paymentTransactionService.failPayment(payment, orderId, "PG 타임아웃", "결제 실패: PG 타임아웃");

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }
}
