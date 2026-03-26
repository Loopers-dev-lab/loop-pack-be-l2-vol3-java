package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgPaymentStatusResponse;
import com.loopers.infrastructure.pg.PgRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F7-1: 유령 결제 복구 시나리오.
 *
 * <p>PG에 요청이 도달했으나 응답 타임아웃 → Payment UNKNOWN.
 * PG는 실제로 결제를 처리한 상태(유령 결제).
 * Polling Hybrid가 PG를 조회하여 PAID로 복구.</p>
 *
 * @see <a href="05-payment-resilience.md §8.7">유령 결제</a>
 */
class GhostPaymentFaultTest {

    private PaymentFacade paymentFacade;
    private PaymentRecoveryService recoveryService;
    private FakePaymentRepository paymentRepository;
    private FakeOrderRepository orderRepository;
    private FakePaymentOutboxRepository outboxRepository;
    private FakePgClient pgClient;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = new FakePaymentRepository();
        orderRepository = new FakeOrderRepository();
        outboxRepository = new FakePaymentOutboxRepository();
        FakeCallbackInboxRepository callbackInboxRepository = new FakeCallbackInboxRepository();
        FakeProductRepository productRepository = new FakeProductRepository();
        FakeCouponIssueRepository couponIssueRepository = new FakeCouponIssueRepository();
        FakeStockReservationRedisRepository stockRedisRepository = new FakeStockReservationRedisRepository();
        pgClient = new FakePgClient("SIMULATOR");
        PgRouter pgRouter = new PgRouter(List.of(pgClient));

        paymentFacade = new PaymentFacade(paymentRepository, orderRepository, pgRouter, outboxRepository);
        setField(paymentFacade, "callbackUrl", "http://test/callback");
        setField(paymentFacade, "maxRetryAttempts", 3);
        setField(paymentFacade, "initialWaitMs", 0L);
        setField(paymentFacade, "backoffMultiplier", 2);

        recoveryService = new PaymentRecoveryService(
            paymentRepository, callbackInboxRepository, orderRepository,
            productRepository, couponIssueRepository, stockRedisRepository, pgRouter);
    }

    @DisplayName("F7-1: 타임아웃 → UNKNOWN → Polling → PG SUCCESS 발견 → PAID 복구")
    @Test
    void ghostPayment_timeout_polling_recovery() throws Exception {
        // Given: 주문 생성
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(1L, "에어맥스", 5000, "나이키", 1)
            )));

        // 1단계: PG 타임아웃 → 모든 요청 실패 → UNKNOWN
        pgClient.setThrowTimeout(true);
        PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
            order.getId(), "SAMSUNG", "1234", 5000);

        assertThat(result.status()).isEqualTo("UNKNOWN");
        PaymentModel unknownPayment = paymentRepository.findById(result.paymentId()).orElseThrow();
        assertThat(unknownPayment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);

        // 2단계: PG 복구 (실제로는 PG가 결제를 처리한 상태)
        // PG 타임아웃 해제 + 유령 결제 상태 등록
        pgClient.setThrowTimeout(false);
        pgClient.registerOrderStatus(String.valueOf(order.getId()),
            new PgPaymentStatusResponse("SUCCESS", "TX-GHOST-001", null));

        // orderId로 Payment를 찾아서 transactionKey 설정 (타임아웃으로 transactionKey 없는 상태)
        // UNKNOWN 상태 Payment는 transactionKey가 없으므로 orderId 기반 폴링
        // → checkPendingPayments에서 getPaymentByOrderId로 조회

        // 3단계: Polling Hybrid 실행 → PG 조회 → SUCCESS → PAID
        recoveryService.checkPendingPayments();

        // Then: Payment → PAID, Order → PAID
        PaymentModel recoveredPayment = paymentRepository.findById(result.paymentId()).orElseThrow();
        assertThat(recoveredPayment.getStatus()).isEqualTo(PaymentStatus.PAID);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @DisplayName("F7-1 변형: PENDING + 콜백 경로로 유령 결제 복구 (transactionKey 보유)")
    @Test
    void ghostPayment_pending_callback_recovery() {
        // Given: PG 호출 성공 → PENDING 상태 (transactionKey 보유)
        // 콜백이 지연/유실되었다가 뒤늦게 도착하는 시나리오
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(1L, "에어맥스", 5000, "나이키", 1)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 5000, "SAMSUNG", "1234");
        payment.markPending("TX-GHOST-002", "SIMULATOR");
        paymentRepository.save(payment);

        // When: PG에서 SUCCESS 콜백 (지연 도착)
        recoveryService.processCallback("TX-GHOST-002", "SUCCESS",
            "{\"orderId\":" + order.getId() + "}");

        // Then: Payment → PAID, Order → PAID
        PaymentModel recovered = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PaymentStatus.PAID);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
