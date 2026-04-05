package com.loopers.application.payment;

import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayOrderResponse;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayRequest;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResponse;
import com.loopers.domain.queue.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentFacade 단위 테스트")
class PaymentFacadeTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private PaymentFacade paymentFacade;

    private Order createTestOrder() {
        List<OrderItem> orderItems = List.of(
                OrderItem.create(1L, "상품A", new BigDecimal("50000"), 1)
        );
        return Order.create(1L, orderItems, BigDecimal.ZERO, null);
    }

    private Order createTestOrderWithCoupon(Long userCouponId) {
        List<OrderItem> orderItems = List.of(
                OrderItem.create(1L, "상품A", new BigDecimal("50000"), 1)
        );
        return Order.create(1L, orderItems, new BigDecimal("5000"), userCouponId);
    }

    @Nested
    @DisplayName("requestPayment - 결제 요청")
    class RequestPayment {

        @Test
        @DisplayName("성공: 결제를 요청하고 PG 호출 후 Payment 상태를 업데이트한다")
        void requestPayment_success() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            Payment payment = Payment.create(orderId, userId, new BigDecimal("50000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            given(paymentTransactionService.preparePayment(orderId, userId, CardType.SAMSUNG, "1234"))
                    .willReturn(payment);

            PaymentGatewayResponse pgResponse = new PaymentGatewayResponse(
                    "txn-abc-123", "PENDING", null
            );
            given(paymentGateway.requestPayment(eq("1"), any(PaymentGatewayRequest.class))).willReturn(pgResponse);

            // When
            PaymentInfo result = paymentFacade.requestPayment(orderId, userId, CardType.SAMSUNG, "1234");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(orderId);
            then(paymentTransactionService).should().preparePayment(orderId, userId, CardType.SAMSUNG, "1234");
            then(paymentGateway).should().requestPayment(eq("1"), any(PaymentGatewayRequest.class));
            then(paymentTransactionService).should().completePayment(payment, "txn-abc-123", "결제 완료");
        }

        @Test
        @DisplayName("성공: PG fallback 시 PENDING 상태를 유지하고 스케줄러에 위임한다")
        void requestPayment_pgFallback_keepsPending() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            Payment payment = Payment.create(orderId, userId, new BigDecimal("50000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            given(paymentTransactionService.preparePayment(orderId, userId, CardType.SAMSUNG, "1234"))
                    .willReturn(payment);

            PaymentGatewayResponse pgResponse = new PaymentGatewayResponse(
                    null, "PENDING", "PG 서비스 불안정으로 결제 처리 대기 중"
            );
            given(paymentGateway.requestPayment(eq("1"), any(PaymentGatewayRequest.class))).willReturn(pgResponse);

            // When
            PaymentInfo result = paymentFacade.requestPayment(orderId, userId, CardType.SAMSUNG, "1234");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            then(paymentTransactionService).should(never()).completePayment(any(), anyString(), anyString());
            then(paymentTransactionService).should(never()).failPayment(any(), anyLong(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("handleCallback - 콜백 처리")
    class HandleCallback {

        @Test
        @DisplayName("성공: SUCCESS 콜백을 받으면 Payment와 Order를 성공 상태로 변경하고 이벤트를 발행한다")
        void handleCallback_success() {
            // Given
            Long orderId = 1L;
            Order order = createTestOrder();
            order.startPayment();
            Payment payment = Payment.create(orderId, 1L, new BigDecimal("50000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            given(paymentService.getByOrderId(orderId)).willReturn(payment);
            given(orderService.getById(orderId)).willReturn(order);

            // When
            paymentFacade.handleCallback(orderId, "txn-abc-123", "SUCCESS", null);

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionKey()).isEqualTo("txn-abc-123");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

            ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            PaymentCompletedEvent event = captor.getValue();
            assertThat(event.orderId()).isEqualTo(orderId);
            assertThat(event.transactionKey()).isEqualTo("txn-abc-123");
        }

        @Test
        @DisplayName("성공: FAILED 콜백을 받으면 Payment와 Order를 실패 상태로 변경하고 이벤트를 발행한다")
        void handleCallback_failed() {
            // Given
            Long orderId = 1L;
            Order order = createTestOrder();
            order.startPayment();
            Payment payment = Payment.create(orderId, 1L, new BigDecimal("50000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            given(paymentService.getByOrderId(orderId)).willReturn(payment);
            given(orderService.getById(orderId)).willReturn(order);

            // When
            paymentFacade.handleCallback(orderId, "txn-abc-123", "FAILED", "잔액 부족");

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("잔액 부족");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            PaymentFailedEvent event = captor.getValue();
            assertThat(event.orderId()).isEqualTo(orderId);
            assertThat(event.userCouponId()).isNull();
        }

        @Test
        @DisplayName("성공: FAILED 콜백 시 쿠폰이 있으면 이벤트에 userCouponId가 포함된다")
        void handleCallback_failed_includesCouponIdInEvent() {
            // Given
            Long orderId = 1L;
            Long userCouponId = 100L;
            Order order = createTestOrderWithCoupon(userCouponId);
            order.startPayment();
            Payment payment = Payment.create(orderId, 1L, new BigDecimal("45000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            given(paymentService.getByOrderId(orderId)).willReturn(payment);
            given(orderService.getById(orderId)).willReturn(order);

            // When
            paymentFacade.handleCallback(orderId, "txn-abc-123", "FAILED", "잔액 부족");

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

            ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().userCouponId()).isEqualTo(userCouponId);
        }
    }

    @Nested
    @DisplayName("getPayment - 결제 조회")
    class GetPayment {

        @Test
        @DisplayName("성공: 주문 ID로 결제 정보를 조회한다")
        void getPayment_success() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            Order order = createTestOrder();
            Payment payment = Payment.create(orderId, userId, new BigDecimal("50000"), CardType.SAMSUNG, "1234");

            given(orderService.getById(orderId)).willReturn(order);
            given(paymentService.getByOrderId(orderId)).willReturn(payment);

            // When
            PaymentInfo result = paymentFacade.getPayment(orderId, userId);

            // Then
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        }
    }

    @Nested
    @DisplayName("recoverPaymentByOrderId - 단건 수동 복구")
    class RecoverPaymentByOrderId {

        @Test
        @DisplayName("성공: orderId로 PENDING 결제를 PG 조회 후 복구한다")
        void recoverPaymentByOrderId_success() {
            // Given
            Long orderId = 1L;
            Payment payment = Payment.create(orderId, 1L, new BigDecimal("50000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            Order order = createTestOrder();
            order.startPayment();

            given(paymentService.getByOrderId(orderId)).willReturn(payment);
            given(paymentGateway.getTransactionsByOrder("1", "1")).willReturn(
                    new PaymentGatewayOrderResponse("1", List.of(
                            new PaymentGatewayResponse("txn-123", "SUCCESS", null)
                    ))
            );
            given(orderService.getById(1L)).willReturn(order);

            // When
            paymentFacade.recoverPaymentByOrderId(orderId);

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionKey()).isEqualTo("txn-123");
        }
    }

    @Nested
    @DisplayName("recoverPendingPayments - 결제 복구")
    class RecoverPendingPayments {

        @Test
        @DisplayName("성공: PENDING 상태 결제를 PG 조회 후 SUCCESS로 복구한다")
        void recoverPendingPayments_success() {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("50000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            Order order = createTestOrder();
            order.startPayment();

            given(paymentService.getPendingPayments()).willReturn(List.of(payment));
            given(paymentGateway.getTransactionsByOrder("1", "1")).willReturn(
                    new PaymentGatewayOrderResponse("1", List.of(
                            new PaymentGatewayResponse("txn-123", "SUCCESS", null)
                    ))
            );
            given(orderService.getById(1L)).willReturn(order);

            // When
            paymentFacade.recoverPendingPayments();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionKey()).isEqualTo("txn-123");
        }

        @Test
        @DisplayName("성공: PG 조회 후 FAILED 확정 시 PaymentFailedEvent를 발행한다")
        void recoverPendingPayments_failed_publishesEvent() {
            // Given
            Long userCouponId = 100L;
            Payment payment = Payment.create(1L, 1L, new BigDecimal("45000"), CardType.SAMSUNG, "1234");
            payment.markPending();

            Order order = createTestOrderWithCoupon(userCouponId);
            order.startPayment();

            given(paymentService.getPendingPayments()).willReturn(List.of(payment));
            given(paymentGateway.getTransactionsByOrder("1", "1")).willReturn(
                    new PaymentGatewayOrderResponse("1", List.of(
                            new PaymentGatewayResponse("txn-123", "FAILED", "잔액 부족")
                    ))
            );
            given(orderService.getById(1L)).willReturn(order);

            // When
            paymentFacade.recoverPendingPayments();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

            ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().userCouponId()).isEqualTo(userCouponId);
        }

        @Test
        @DisplayName("성공: PG에 거래 내역이 없고 TTL 이내이면 PENDING 상태를 유지한다")
        void recoverPendingPayments_noTransactions_withinTtl() throws Exception {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("50000"), CardType.KB, "1234");
            payment.markPending();
            setCreatedAt(payment, ZonedDateTime.now().minusMinutes(2));

            given(paymentService.getPendingPayments()).willReturn(List.of(payment));
            given(paymentGateway.getTransactionsByOrder("1", "1")).willReturn(
                    new PaymentGatewayOrderResponse("1", List.of())
            );

            // When
            paymentFacade.recoverPendingPayments();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            then(orderService).should(never()).getById(anyLong());
        }

        @Test
        @DisplayName("성공: PG에 거래 내역이 없고 TTL 초과 시 FAILED 처리하고 이벤트를 발행한다")
        void recoverPendingPayments_noTransactions_ttlExpired() throws Exception {
            // Given
            Long userCouponId = 100L;
            Payment payment = Payment.create(1L, 1L, new BigDecimal("45000"), CardType.KB, "1234");
            payment.markPending();
            setCreatedAt(payment, ZonedDateTime.now().minusMinutes(5));

            Order order = createTestOrderWithCoupon(userCouponId);
            order.startPayment();

            given(paymentService.getPendingPayments()).willReturn(List.of(payment));
            given(paymentGateway.getTransactionsByOrder("1", "1")).willReturn(
                    new PaymentGatewayOrderResponse("1", List.of())
            );
            given(orderService.getById(1L)).willReturn(order);

            // When
            paymentFacade.recoverPendingPayments();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().userCouponId()).isEqualTo(userCouponId);
        }
    }

    private void setCreatedAt(Payment payment, ZonedDateTime createdAt) throws Exception {
        Field field = payment.getClass().getSuperclass().getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(payment, createdAt);
    }
}
