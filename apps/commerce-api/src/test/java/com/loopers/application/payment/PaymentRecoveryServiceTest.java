package com.loopers.application.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.*;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
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

class PaymentRecoveryServiceTest {

    private PaymentRecoveryService recoveryService;
    private FakePaymentRepository paymentRepository;
    private FakeCallbackInboxRepository callbackInboxRepository;
    private FakeOrderRepository orderRepository;
    private FakeProductRepository productRepository;
    private FakeCouponIssueRepository couponIssueRepository;
    private FakeStockReservationRedisRepository stockRedisRepository;
    private FakePgClient pgClient;

    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        callbackInboxRepository = new FakeCallbackInboxRepository();
        orderRepository = new FakeOrderRepository();
        productRepository = new FakeProductRepository();
        couponIssueRepository = new FakeCouponIssueRepository();
        stockRedisRepository = new FakeStockReservationRedisRepository();
        pgClient = new FakePgClient("SIMULATOR");

        PgRouter pgRouter = new PgRouter(List.of(pgClient));

        recoveryService = new PaymentRecoveryService(
            paymentRepository, callbackInboxRepository, orderRepository,
            productRepository, couponIssueRepository, stockRedisRepository, pgRouter);
    }

    @DisplayName("U4-5: Polling — PG SUCCESS → PAID 전이")
    @Test
    void polling_pgSuccess_transitionToPaid() throws Exception {
        Product product = productRepository.save(
            new Product(1L, "에어맥스", new Price(5000), new Stock(100)));
        stockRedisRepository.setStock(product.getId(), 100);

        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 2)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 10000, "SAMSUNG", "1234");
        payment.markPending("TX-POLL-001", "SIMULATOR");
        payment = paymentRepository.save(payment);

        // createdAt을 15초 전으로 설정 (10초 threshold 초과)
        setCreatedAt(payment, ZonedDateTime.now().minusSeconds(15));

        // PG에 SUCCESS 상태 등록
        pgClient.registerStatus("TX-POLL-001",
            new PgPaymentStatusResponse("SUCCESS", "TX-POLL-001", null));

        recoveryService.checkPendingPayments();

        // Payment → PAID
        PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @DisplayName("U4-6: Polling — PG PENDING + 생성 5초 → 유지 (threshold 미달)")
    @Test
    void polling_pendingUnderThreshold_noChange() throws Exception {
        Product product = productRepository.save(
            new Product(1L, "에어맥스", new Price(5000), new Stock(100)));

        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 2)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 10000, "SAMSUNG", "1234");
        payment.markPending("TX-POLL-002", "SIMULATOR");
        payment = paymentRepository.save(payment);

        // createdAt을 5초 전으로 설정 (10초 threshold 미달)
        setCreatedAt(payment, ZonedDateTime.now().minusSeconds(5));

        pgClient.registerStatus("TX-POLL-002",
            new PgPaymentStatusResponse("PENDING", "TX-POLL-002", null));

        recoveryService.checkPendingPayments();

        // Payment 상태 변경 없음 (PENDING 유지)
        PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    private void setCreatedAt(Object entity, ZonedDateTime createdAt) throws Exception {
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(entity, createdAt);
    }
}
