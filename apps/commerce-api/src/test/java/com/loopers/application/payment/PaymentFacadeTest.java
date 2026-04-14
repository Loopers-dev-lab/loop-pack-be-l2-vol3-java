package com.loopers.application.payment;

import com.loopers.application.outbox.OutboxEventPublisher;
import com.loopers.domain.order.InMemoryOrderItemRepository;
import com.loopers.domain.order.InMemoryOrderRepository;
import com.loopers.domain.order.OrderItem;
import com.loopers.event.EventType;
import com.loopers.event.payload.PaymentCompletedEventPayload;
import com.loopers.application.order.OrderCompensationService;
import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.InMemoryPaymentRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.client.PgDeclinedException;
import com.loopers.infrastructure.client.PgPaymentDto;
import com.loopers.infrastructure.client.PgPaymentException;
import com.loopers.infrastructure.client.PgPaymentGateway;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import org.mockito.ArgumentCaptor;

class PaymentFacadeTest {

    private InMemoryPaymentRepository paymentRepository;
    private InMemoryOrderRepository orderRepository;
    private InMemoryOrderItemRepository orderItemRepository;
    private OrderService orderService;
    private OrderCompensationService orderCompensationService;
    private OutboxEventPublisher outboxEventPublisher;
    private PgPaymentGateway pgPaymentGateway;
    private PaymentFacade paymentFacade;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        orderRepository = new InMemoryOrderRepository();
        orderItemRepository = new InMemoryOrderItemRepository();
        orderService = new OrderService(orderRepository, orderItemRepository);
        orderCompensationService = mock(OrderCompensationService.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        pgPaymentGateway = mock(PgPaymentGateway.class);
        paymentFacade = new PaymentFacade(paymentRepository, orderService, orderCompensationService, outboxEventPublisher, pgPaymentGateway, "http://localhost:8080/api/v1/payments/callback");
    }

    @DisplayName("결제 요청 시, ")
    @Nested
    class RequestPayment {

        @DisplayName("PG가 명시적으로 거절하면 즉시 FAILED 처리되고 보상이 실행된다.")
        @Test
        void failsImmediately_whenPgDeclines() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));
            given(pgPaymentGateway.requestPayment(anyString(), any()))
                    .willThrow(new PgDeclinedException("카드 한도 초과"));

            // act
            PaymentInfo result = paymentFacade.requestPayment(1L, new PaymentCommand(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456"));

            // assert
            assertAll(
                    () -> assertThat(result.status()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(result.failReason()).isEqualTo("카드 한도 초과"),
                    () -> then(orderCompensationService).should().compensate(order.getId())
            );
        }

        @DisplayName("PG 일시 장애(timeout/circuit open)면 PENDING 유지된다.")
        @Test
        void staysPending_whenPgTemporaryFailure() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));
            given(pgPaymentGateway.requestPayment(anyString(), any()))
                    .willThrow(new PgPaymentException("PG 결제 요청 불가: Read timed out"));

            // act
            PaymentInfo result = paymentFacade.requestPayment(1L, new PaymentCommand(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456"));

            // assert
            assertAll(
                    () -> assertThat(result.status()).isEqualTo(PaymentStatus.PENDING),
                    () -> then(orderCompensationService).shouldHaveNoInteractions()
            );
        }
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

        @DisplayName("SUCCESS 콜백 시 발행되는 이벤트에 상품별 결제 금액 정보가 포함된다.")
        @Test
        void publishesPaymentEventWithOrderedProducts_whenSuccess() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(
                    new OrderItemSnapshot(1L, "상품A", 10000L, 2),
                    new OrderItemSnapshot(2L, "상품B", 5000L, 1)
            )));
            orderItemRepository.saveAll(List.of(
                    OrderItem.create(order.getId(), 1L, "상품A", 10000L, 2),
                    OrderItem.create(order.getId(), 2L, "상품B", 5000L, 1)
            ));
            Payment payment = paymentRepository.save(Payment.create(order.getId(), "pgOrderCode-010", CardType.SAMSUNG, "1234-5678-9012-3456", 25000L));
            payment.assignPgTransaction("TXN-010");

            // act
            paymentFacade.handleCallback(new PgCallbackCommand("TXN-010", "SUCCESS", null));

            // assert
            ArgumentCaptor<PaymentCompletedEventPayload> captor = ArgumentCaptor.forClass(PaymentCompletedEventPayload.class);
            then(outboxEventPublisher).should().publish(eq(EventType.PAYMENT_COMPLETED), captor.capture(), eq(order.getId()));

            PaymentCompletedEventPayload payload = captor.getValue();
            assertAll(
                    () -> assertThat(payload.getOrderedProducts()).hasSize(2),
                    () -> assertThat(payload.getOrderedProducts())
                            .extracting("productId", "price", "quantity")
                            .containsExactly(
                                    tuple(1L, 10000L, 2),
                                    tuple(2L, 5000L, 1)
                            )
            );
        }

        @DisplayName("FAILED 콜백을 받으면 Payment는 FAILED로 전환되고 compensate()가 호출된다.")
        @Test
        void failsPaymentAndCallsCompensate_whenFailed() {
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
                    () -> then(orderCompensationService).should().compensate(order.getId())
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
