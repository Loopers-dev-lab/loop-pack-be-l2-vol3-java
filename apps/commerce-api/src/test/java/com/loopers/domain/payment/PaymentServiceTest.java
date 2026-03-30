package com.loopers.domain.payment;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, orderRepository);
    }

    @DisplayName("외부 결제 결과 반영 시, ")
    @Nested
    class ApplyExternalResult {

        @DisplayName("성공 결과면 결제/주문 상태가 SUCCESS/PAID 로 반영된다.")
        @Test
        void marksPaymentAndOrderAsPaid_whenSuccessResultComes() {
            PaymentModel payment = new PaymentModel(1L, 10L, 5000L, CardType.SAMSUNG, "1234-5678-1111-2222");
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "상품", 5000L, 1)));

            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            PaymentModel result = paymentService.applyExternalResult(1L, "20260319:TR:paid", PaymentStatus.SUCCESS, null);

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(order.getStatus().name()).isEqualTo("PAID")
            );
        }

        @DisplayName("실패 결과면 결제/주문 상태가 실패/PAYMENT_FAILED 로 반영된다.")
        @Test
        void marksPaymentAndOrderAsFailed_whenFailureResultComes() {
            PaymentModel payment = new PaymentModel(1L, 10L, 5000L, CardType.SAMSUNG, "1234-5678-1111-2222");
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "상품", 5000L, 1)));

            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            PaymentModel result = paymentService.applyExternalResult(
                1L,
                "20260319:TR:failed",
                PaymentStatus.FAILED_INVALID_CARD,
                "invalid card"
            );

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED_INVALID_CARD),
                () -> assertThat(order.getStatus().name()).isEqualTo("PAYMENT_FAILED")
            );
        }
    }
}
