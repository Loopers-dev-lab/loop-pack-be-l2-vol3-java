package com.loopers.application.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentCallbackTest {

    private PaymentRecoveryService recoveryService;
    private FakePaymentRepository paymentRepository;
    private FakeCallbackInboxRepository callbackInboxRepository;
    private FakeOrderRepository orderRepository;
    private FakeProductRepository productRepository;
    private FakeCouponIssueRepository couponIssueRepository;
    private FakeStockReservationRedisRepository stockRedisRepository;
    private FakePgClient pgClient;

    @SuppressWarnings("unchecked")
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

        ProductFacade productFacade = new ProductFacade(
            productRepository, new FakeBrandRepository(), new FakeLikeRepository(),
            new FakeProductCachePort(), event -> {}, stockRedisRepository);

        CouponIssueRequestRepository issueRequestRepository = new CouponIssueRequestRepository() {
            @Override public CouponIssueRequest save(CouponIssueRequest request) { return request; }
            @Override public Optional<CouponIssueRequest> findById(Long id) { return Optional.empty(); }
        };
        CouponFacade couponFacade = new CouponFacade(new FakeCouponRepository(), couponIssueRepository,
            issueRequestRepository, mock(KafkaTemplate.class), new ObjectMapper(), Clock.systemDefaultZone());

        recoveryService = new PaymentRecoveryService(
            paymentRepository, new FakePaymentStatusHistoryRepository(),
            callbackInboxRepository, orderRepository,
            productFacade, couponFacade, pgRouter);
    }

    private Order createOrderWithProduct() {
        Product product = productRepository.save(
            new Product(1L, "에어맥스", new Price(5000), new Stock(100)));
        stockRedisRepository.setStock(product.getId(), 100);

        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 2)
            )));
        return order;
    }

    private PaymentModel createPendingPayment(Order order) {
        PaymentModel payment = PaymentModel.create(order.getId(), 10000, "SAMSUNG", "1234");
        payment.markPending("TX-001", "SIMULATOR");
        return paymentRepository.save(payment);
    }

    @DisplayName("U4-1: SUCCESS 콜백 → Payment PAID + Order PAID")
    @Test
    void callback_success_paidPaymentAndOrder() {
        Order order = createOrderWithProduct();
        PaymentModel payment = createPendingPayment(order);

        recoveryService.processCallback("TX-001", "SUCCESS", "{\"status\":\"SUCCESS\"}");

        // Payment → PAID
        PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PAID);

        // Order → PAID
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // Inbox → PROCESSED
        List<CallbackInbox> inboxes = callbackInboxRepository.findAllByTransactionKey("TX-001");
        assertThat(inboxes).hasSize(1);
        assertThat(inboxes.get(0).getStatus()).isEqualTo(CallbackInboxStatus.PROCESSED);
    }

    @DisplayName("U4-2: FAILED 콜백 → Payment FAILED + 재고 복원 + 쿠폰 복원")
    @Test
    void callback_failed_restoresStockAndCoupon() {
        Product product = productRepository.save(
            new Product(1L, "에어맥스", new Price(5000), new Stock(98)));
        stockRedisRepository.setStock(product.getId(), 98);

        // 쿠폰 사용 상태로 설정
        CouponIssue couponIssue = new CouponIssue(1L, 100L, ZonedDateTime.now().plusDays(30));
        couponIssue = couponIssueRepository.save(couponIssue);
        couponIssue.use(1L, ZonedDateTime.now());
        couponIssueRepository.save(couponIssue);

        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 2)
            ), couponIssue.getId(), 1000));

        PaymentModel payment = createPendingPayment(order);

        recoveryService.processCallback("TX-001", "FAILED", "{\"status\":\"FAILED\"}");

        // Payment → FAILED
        PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // 재고 복원 확인 (Redis)
        assertThat(stockRedisRepository.getStock(product.getId())).isEqualTo(100L);

        // 재고 복원 확인 (DB)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(100);

        // 쿠폰 복원 확인
        CouponIssue updatedCoupon = couponIssueRepository.findById(couponIssue.getId()).orElseThrow();
        assertThat(updatedCoupon.getStatus().name()).isEqualTo("AVAILABLE");
    }

    @DisplayName("U4-3: PENDING 콜백 → 무시 (상태 변경 없음)")
    @Test
    void callback_pending_ignored() {
        Order order = createOrderWithProduct();
        PaymentModel payment = createPendingPayment(order);

        recoveryService.processCallback("TX-001", "PENDING", "{\"status\":\"PENDING\"}");

        // Payment 상태 변경 없음 (PENDING 유지)
        PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // Inbox → PROCESSED (정상 처리되었으나 무시됨)
        List<CallbackInbox> inboxes = callbackInboxRepository.findAllByTransactionKey("TX-001");
        assertThat(inboxes).hasSize(1);
        assertThat(inboxes.get(0).getStatus()).isEqualTo(CallbackInboxStatus.PROCESSED);
    }

    @DisplayName("U4-4: 존재하지 않는 transactionKey → 로그 남기고 무시")
    @Test
    void callback_unknownTransactionKey_ignored() {
        recoveryService.processCallback("TX-UNKNOWN", "SUCCESS", "{\"status\":\"SUCCESS\"}");

        // Inbox에 저장되었지만 FAILED 상태
        List<CallbackInbox> inboxes = callbackInboxRepository.findAllByTransactionKey("TX-UNKNOWN");
        assertThat(inboxes).hasSize(1);
        assertThat(inboxes.get(0).getStatus()).isEqualTo(CallbackInboxStatus.FAILED);
        assertThat(inboxes.get(0).getErrorMessage()).contains("Payment not found");
    }
}
