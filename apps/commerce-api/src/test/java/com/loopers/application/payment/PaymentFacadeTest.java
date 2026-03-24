package com.loopers.application.payment;

import com.loopers.application.order.OrderAppService;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgPaymentStatusResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PaymentFacade 단위 테스트")
class PaymentFacadeTest {

    private PaymentFacade paymentFacade;
    private PaymentAppService paymentAppService;
    private OrderAppService orderAppService;
    private PgClient pgClient;
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() throws InterruptedException {
        paymentAppService = mock(PaymentAppService.class);
        orderAppService = mock(OrderAppService.class);
        pgClient = mock(PgClient.class);
        redissonClient = mock(RedissonClient.class);

        RLock rLock = mock(RLock.class);
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(eq(0L), eq(15L), eq(TimeUnit.SECONDS))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        paymentFacade = new PaymentFacade(paymentAppService, orderAppService, pgClient, redissonClient);
        ReflectionTestUtils.setField(paymentFacade, "callbackUrl", "http://localhost:8080/api/v1/payments/callback");
    }

    @Nested
    @DisplayName("결제 요청 (requestPayment)")
    class RequestPaymentTest {

        @Test
        @DisplayName("결제 요청 시 PENDING 상태의 Payment가 생성된다")
        void requestPayment_success() {
            Long userId = 1L;
            Long orderId = 10L;

            Order order = mock(Order.class);
            given(order.getPaymentAmount()).willReturn(Money.of(50000L));
            given(orderAppService.getById(orderId)).willReturn(order);

            Payment payment = mock(Payment.class);
            given(payment.getId()).willReturn(100L);
            given(payment.getOrderId()).willReturn(orderId);
            given(payment.getUserId()).willReturn(userId);
            given(payment.getCardType()).willReturn("VISA");
            given(payment.getCardNo()).willReturn("4111");
            given(payment.getAmount()).willReturn(Money.of(50000L));
            given(payment.getStatus()).willReturn(PaymentStatus.PENDING);

            given(paymentAppService.createPayment(eq(orderId), eq(userId), eq("VISA"), eq("4111"), any()))
                    .willReturn(payment);
            given(pgClient.requestPayment(any(PgPaymentCommand.class)))
                    .willReturn(new PgPaymentResult(true, "txn-abc", "접수 완료"));

            PaymentInfo result = paymentFacade.requestPayment(userId, orderId, "VISA", "4111");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getOrderId()).isEqualTo(orderId);
            verify(paymentAppService).createPayment(eq(orderId), eq(userId), eq("VISA"), eq("4111"), any());
            verify(pgClient).requestPayment(any(PgPaymentCommand.class));
        }

        @Test
        @DisplayName("PG 호출이 Fallback되어도 PENDING 상태가 유지된다")
        void requestPayment_pgFallback_pendingMaintained() {
            Long userId = 1L;
            Long orderId = 10L;

            Order order = mock(Order.class);
            given(order.getPaymentAmount()).willReturn(Money.of(50000L));
            given(orderAppService.getById(orderId)).willReturn(order);

            Payment payment = mock(Payment.class);
            given(payment.getId()).willReturn(100L);
            given(payment.getOrderId()).willReturn(orderId);
            given(payment.getUserId()).willReturn(userId);
            given(payment.getCardType()).willReturn("VISA");
            given(payment.getCardNo()).willReturn("4111");
            given(payment.getAmount()).willReturn(Money.of(50000L));
            given(payment.getStatus()).willReturn(PaymentStatus.PENDING);

            given(paymentAppService.createPayment(eq(orderId), eq(userId), eq("VISA"), eq("4111"), any()))
                    .willReturn(payment);
            given(pgClient.requestPayment(any(PgPaymentCommand.class)))
                    .willReturn(PgPaymentResult.fallback("PG 응답 지연"));

            PaymentInfo result = paymentFacade.requestPayment(userId, orderId, "VISA", "4111");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentAppService, never()).completePayment(any(), any(), any());
            verify(paymentAppService, never()).failPayment(any(), any());
        }
    }

    @Nested
    @DisplayName("콜백 처리 (handleCallback)")
    class HandleCallbackTest {

        @Test
        @DisplayName("SUCCESS 콜백을 받으면 Payment를 완료하고 Order를 결제 처리한다")
        void handleCallback_success() {
            Long orderId = 10L;
            Long paymentId = 100L;

            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(paymentId);
            given(pendingPayment.getOrderId()).willReturn(orderId);
            given(pendingPayment.getUserId()).willReturn(1L);
            given(pendingPayment.getStatus()).willReturn(PaymentStatus.PENDING);
            given(pendingPayment.getAmount()).willReturn(Money.of(50000L));
            given(paymentAppService.getByOrderIdAndActiveStatus(orderId)).willReturn(pendingPayment);

            Payment completedPayment = mock(Payment.class);
            given(completedPayment.getId()).willReturn(paymentId);
            given(completedPayment.getOrderId()).willReturn(orderId);
            given(completedPayment.getUserId()).willReturn(1L);
            given(completedPayment.getStatus()).willReturn(PaymentStatus.SUCCESS);
            given(completedPayment.getAmount()).willReturn(Money.of(50000L));
            given(completedPayment.getTransactionId()).willReturn("txn-123");
            given(paymentAppService.completePayment(paymentId, "txn-123", "결제 성공")).willReturn(completedPayment);

            PaymentInfo result = paymentFacade.handleCallback(orderId, "txn-123", "SUCCESS", "결제 성공");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentAppService).completePayment(paymentId, "txn-123", "결제 성공");
            verify(orderAppService).pay(orderId);
        }

        @Test
        @DisplayName("FAIL 콜백을 받으면 Payment를 실패 처리하고 Order는 변경하지 않는다")
        void handleCallback_fail() {
            Long orderId = 10L;
            Long paymentId = 100L;

            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(paymentId);
            given(pendingPayment.getOrderId()).willReturn(orderId);
            given(pendingPayment.getStatus()).willReturn(PaymentStatus.PENDING);
            given(pendingPayment.getAmount()).willReturn(Money.of(50000L));
            given(paymentAppService.getByOrderIdAndActiveStatus(orderId)).willReturn(pendingPayment);

            Payment failedPayment = mock(Payment.class);
            given(failedPayment.getId()).willReturn(paymentId);
            given(failedPayment.getOrderId()).willReturn(orderId);
            given(failedPayment.getStatus()).willReturn(PaymentStatus.FAIL);
            given(failedPayment.getAmount()).willReturn(Money.of(50000L));
            given(failedPayment.getPgResponseMessage()).willReturn("한도 초과");
            given(paymentAppService.failPayment(paymentId, "한도 초과")).willReturn(failedPayment);

            PaymentInfo result = paymentFacade.handleCallback(orderId, null, "FAIL", "한도 초과");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAIL);
            verify(paymentAppService).failPayment(paymentId, "한도 초과");
            verify(orderAppService, never()).pay(any());
        }

        @Test
        @DisplayName("이미 터미널 상태인 결제에 대한 콜백은 무시된다 (멱등)")
        void handleCallback_alreadyTerminal_idempotent() {
            Long orderId = 10L;

            Payment completedPayment = mock(Payment.class);
            given(completedPayment.getId()).willReturn(100L);
            given(completedPayment.getOrderId()).willReturn(orderId);
            given(completedPayment.getUserId()).willReturn(1L);
            given(completedPayment.getStatus()).willReturn(PaymentStatus.SUCCESS);
            given(completedPayment.getAmount()).willReturn(Money.of(50000L));
            given(completedPayment.getTransactionId()).willReturn("txn-123");
            given(paymentAppService.getByOrderIdAndActiveStatus(orderId)).willReturn(completedPayment);

            PaymentInfo result = paymentFacade.handleCallback(orderId, "txn-123", "SUCCESS", "결제 성공");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentAppService, never()).completePayment(any(), any(), any());
            verify(paymentAppService, never()).failPayment(any(), any());
            verify(orderAppService, never()).pay(any());
        }
    }

    @Nested
    @DisplayName("수동 동기화 (syncPendingPayments)")
    class SyncPendingPaymentsTest {

        @Test
        @DisplayName("PENDING 결제를 PG에서 SUCCESS로 확인하면 완료 처리한다")
        void sync_pgSuccess() {
            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(100L);
            given(pendingPayment.getOrderId()).willReturn(10L);
            given(pendingPayment.getUserId()).willReturn(1L);
            given(pendingPayment.getStatus()).willReturn(PaymentStatus.PENDING);
            given(pendingPayment.getAmount()).willReturn(Money.of(50000L));
            given(paymentAppService.getPendingPayments()).willReturn(List.of(pendingPayment));

            given(pgClient.getPaymentStatus(10L, 1L))
                    .willReturn(new PgPaymentStatusResult("txn-123", "SUCCESS", "결제 완료"));

            Payment completedPayment = mock(Payment.class);
            given(completedPayment.getId()).willReturn(100L);
            given(completedPayment.getOrderId()).willReturn(10L);
            given(completedPayment.getUserId()).willReturn(1L);
            given(completedPayment.getStatus()).willReturn(PaymentStatus.SUCCESS);
            given(completedPayment.getAmount()).willReturn(Money.of(50000L));
            given(completedPayment.getTransactionId()).willReturn("txn-123");
            given(paymentAppService.completePayment(100L, "txn-123", "결제 완료")).willReturn(completedPayment);

            List<PaymentInfo> results = paymentFacade.syncPendingPayments();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(orderAppService).pay(10L);
        }

        @Test
        @DisplayName("PG 상태가 UNKNOWN이면 PENDING을 유지한다")
        void sync_pgUnknown_pendingMaintained() {
            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(100L);
            given(pendingPayment.getOrderId()).willReturn(10L);
            given(pendingPayment.getUserId()).willReturn(1L);
            given(pendingPayment.getStatus()).willReturn(PaymentStatus.PENDING);
            given(pendingPayment.getAmount()).willReturn(Money.of(50000L));
            given(paymentAppService.getPendingPayments()).willReturn(List.of(pendingPayment));

            given(pgClient.getPaymentStatus(10L, 1L))
                    .willReturn(new PgPaymentStatusResult(null, "UNKNOWN", "상태 조회 불가"));

            List<PaymentInfo> results = paymentFacade.syncPendingPayments();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentAppService, never()).completePayment(any(), any(), any());
            verify(paymentAppService, never()).failPayment(any(), any());
            verify(orderAppService, never()).pay(any());
        }

        @Test
        @DisplayName("PG 상태가 FAIL이면 결제를 실패 처리한다")
        void sync_pgFail() {
            Payment pendingPayment = mock(Payment.class);
            given(pendingPayment.getId()).willReturn(100L);
            given(pendingPayment.getOrderId()).willReturn(10L);
            given(pendingPayment.getUserId()).willReturn(1L);
            given(pendingPayment.getStatus()).willReturn(PaymentStatus.PENDING);
            given(pendingPayment.getAmount()).willReturn(Money.of(50000L));
            given(paymentAppService.getPendingPayments()).willReturn(List.of(pendingPayment));

            given(pgClient.getPaymentStatus(10L, 1L))
                    .willReturn(new PgPaymentStatusResult(null, "FAIL", "카드 한도 초과"));

            Payment failedPayment = mock(Payment.class);
            given(failedPayment.getId()).willReturn(100L);
            given(failedPayment.getOrderId()).willReturn(10L);
            given(failedPayment.getUserId()).willReturn(1L);
            given(failedPayment.getStatus()).willReturn(PaymentStatus.FAIL);
            given(failedPayment.getAmount()).willReturn(Money.of(50000L));
            given(paymentAppService.failPayment(100L, "카드 한도 초과")).willReturn(failedPayment);

            List<PaymentInfo> results = paymentFacade.syncPendingPayments();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(PaymentStatus.FAIL);
            verify(orderAppService, never()).pay(any());
        }
    }
}
