package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.InMemoryOrderItemRepository;
import com.loopers.domain.order.InMemoryOrderRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.InMemoryPaymentRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentFacadeTest {

    private InMemoryPaymentRepository paymentRepository;
    private InMemoryOrderRepository orderRepository;
    private OrderService orderService;
    private PaymentFacade paymentFacade;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        orderRepository = new InMemoryOrderRepository();
        orderService = new OrderService(orderRepository, new InMemoryOrderItemRepository());
        paymentFacade = new PaymentFacade(paymentRepository, orderService);
    }

    @DisplayName("PG 콜백 수신 시, ")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백을 받으면 Payment는 COMPLETED, Order는 PAID로 전환된다.")
        @Test
        void completesPaymentAndMarksOrderPaid_whenSuccess() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));
            Payment payment = paymentRepository.save(Payment.create(order.getId(), "pgOrderCode-001", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            payment.assignPgTransaction("TXN-001");

            // act
            paymentFacade.handleCallback(new PgCallbackCommand("TXN-001", "SUCCESS", null));

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED),
                    () -> assertThat(orderRepository.findById(order.getId()).get().getStatus()).isEqualTo(Order.Status.PAID)
            );
        }

        @DisplayName("FAILED 콜백을 받으면 Payment는 FAILED, Order는 FAILED로 전환된다.")
        @Test
        void failsPaymentAndMarksOrderFailed_whenFailed() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));
            Payment payment = paymentRepository.save(Payment.create(order.getId(), "pgOrderCode-002", CardType.KB, "1234-5678-9012-3456", 10000L));
            payment.assignPgTransaction("TXN-002");

            // act
            paymentFacade.handleCallback(new PgCallbackCommand("TXN-002", "FAILED", "한도초과입니다."));

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailReason()).isEqualTo("한도초과입니다."),
                    () -> assertThat(orderRepository.findById(order.getId()).get().getStatus()).isEqualTo(Order.Status.FAILED)
            );
        }

        @DisplayName("존재하지 않는 transactionKey로 콜백이 오면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTransactionKeyNotFound() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentFacade.handleCallback(new PgCallbackCommand("UNKNOWN-TXN", "SUCCESS", null))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("처리할 수 없는 PG 상태로 콜백이 오면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUnknownStatus() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));
            Payment payment = paymentRepository.save(Payment.create(order.getId(), "pgOrderCode-003", CardType.HYUNDAI, "1234-5678-9012-3456", 10000L));
            payment.assignPgTransaction("TXN-003");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentFacade.handleCallback(new PgCallbackCommand("TXN-003", "UNKNOWN", null))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
