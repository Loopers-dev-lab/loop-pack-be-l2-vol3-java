package com.loopers.application.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgPaymentStatusResponse;
import com.loopers.infrastructure.pg.PgRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * F7-3: 콜백 미수신 → Polling Hybrid 복구 시나리오.
 *
 * <p>PG 결제 요청 성공 → Payment PENDING → 콜백이 오지 않는 상황.
 * 10초 후 Polling Hybrid가 PG를 조회하여 결과를 확인.</p>
 *
 * @see <a href="05-payment-resilience.md §8.4">Polling Hybrid</a>
 */
class CallbackMissFaultTest {

    private PaymentRecoveryService recoveryService;
    private FakePaymentRepository paymentRepository;
    private FakeOrderRepository orderRepository;
    private FakePgClient pgClient;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        orderRepository = new FakeOrderRepository();
        FakeCallbackInboxRepository callbackInboxRepository = new FakeCallbackInboxRepository();
        FakeProductRepository productRepository = new FakeProductRepository();
        FakeStockReservationRedisRepository stockRedisRepository = new FakeStockReservationRedisRepository();
        pgClient = new FakePgClient("SIMULATOR");
        PgRouter pgRouter = new PgRouter(List.of(pgClient));

        ProductFacade productFacade = new ProductFacade(
            productRepository, new FakeBrandRepository(), new FakeLikeRepository(),
            new FakeProductCachePort(), event -> {}, stockRedisRepository);

        CouponIssueRequestRepository issueRequestRepository = new CouponIssueRequestRepository() {
            @Override public CouponIssueRequest save(CouponIssueRequest request) { return request; }
            @Override public Optional<CouponIssueRequest> findById(Long id) { return Optional.empty(); }
        };
        CouponFacade couponFacade = new CouponFacade(new FakeCouponRepository(), new FakeCouponIssueRepository(),
            issueRequestRepository, mock(KafkaTemplate.class), new ObjectMapper(), Clock.systemDefaultZone());

        recoveryService = new PaymentRecoveryService(
            paymentRepository, new FakePaymentStatusHistoryRepository(),
            callbackInboxRepository, orderRepository,
            productFacade, couponFacade, pgRouter);
    }

    @DisplayName("F7-3: PENDING → 콜백 미수신 → 10초 후 Polling → PG SUCCESS → PAID")
    @Test
    void callbackMiss_polling_recovery() throws Exception {
        // Given: 주문 + Payment(PENDING) 생성, 콜백 미수신
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(1L, "에어맥스", 5000, "나이키", 1)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 5000, "SAMSUNG", "1234");
        payment.markPending("TX-MISS-001", "SIMULATOR");
        payment = paymentRepository.save(payment);

        // 10초 이상 경과 시뮬레이션 (Polling 대상)
        setCreatedAt(payment, ZonedDateTime.now().minusSeconds(15));

        // PG에는 SUCCESS 상태 (콜백은 유실되었지만 PG는 정상 처리)
        pgClient.registerStatus("TX-MISS-001",
            new PgPaymentStatusResponse("SUCCESS", "TX-MISS-001", null));

        // When: Polling Hybrid 실행
        recoveryService.checkPendingPayments();

        // Then: Payment → PAID, Order → PAID
        PaymentModel recovered = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PaymentStatus.PAID);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @DisplayName("F7-3 변형: PENDING → 콜백 미수신 → Polling → PG FAILED → 재고/쿠폰 복원")
    @Test
    void callbackMiss_polling_pgFailed_restore() throws Exception {
        // Given: 주문 + Payment(PENDING)
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(1L, "에어맥스", 5000, "나이키", 1)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 5000, "SAMSUNG", "1234");
        payment.markPending("TX-MISS-002", "SIMULATOR");
        payment = paymentRepository.save(payment);
        setCreatedAt(payment, ZonedDateTime.now().minusSeconds(15));

        // PG에는 FAILED 상태
        pgClient.registerStatus("TX-MISS-002",
            new PgPaymentStatusResponse("FAILED", "TX-MISS-002", "잔액 부족"));

        // When: Polling 실행
        recoveryService.checkPendingPayments();

        // Then: Payment → FAILED
        PaymentModel recovered = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @DisplayName("최근 PENDING (10초 미만) → Polling 대상 아님")
    @Test
    void recentPending_notPolled() throws Exception {
        // Given: 5초 전 생성된 PENDING Payment
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(1L, "에어맥스", 5000, "나이키", 1)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 5000, "SAMSUNG", "1234");
        payment.markPending("TX-RECENT", "SIMULATOR");
        payment = paymentRepository.save(payment);
        setCreatedAt(payment, ZonedDateTime.now().minusSeconds(5));

        pgClient.registerStatus("TX-RECENT",
            new PgPaymentStatusResponse("SUCCESS", "TX-RECENT", null));

        // When: Polling 실행
        recoveryService.checkPendingPayments();

        // Then: 아직 PENDING (10초 미만이므로 폴링 대상 아님)
        PaymentModel stillPending = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(stillPending.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    private void setCreatedAt(Object entity, ZonedDateTime createdAt) throws Exception {
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(entity, createdAt);
    }
}
