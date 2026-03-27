package com.loopers.application.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ManualRecoveryTest {

    private PaymentRecoveryService recoveryService;
    private FakePaymentRepository paymentRepository;
    private FakeOrderRepository orderRepository;
    private FakePgClient pgClient;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        FakeCallbackInboxRepository callbackInboxRepository = new FakeCallbackInboxRepository();
        orderRepository = new FakeOrderRepository();
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

    @DisplayName("U5-9: confirm API → PG 조회 → PAID 전이")
    @Test
    void manualConfirm_pgSuccess_transitionToPaid() {
        Product product = new Product(1L, "에어맥스", new Price(5000), new Stock(100));
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 1)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 5000, "SAMSUNG", "1234");
        payment.markPending("TX-CONFIRM-001", "SIMULATOR");
        payment = paymentRepository.save(payment);

        // PG에 SUCCESS 상태 등록
        pgClient.registerStatus("TX-CONFIRM-001",
            new PgPaymentStatusResponse("SUCCESS", "TX-CONFIRM-001", null));

        String result = recoveryService.manualConfirm(payment.getId());

        assertThat(result).contains("PAID");
        PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @DisplayName("이미 최종 상태인 결제건 → 변경 없음")
    @Test
    void manualConfirm_alreadyTerminal_noChange() {
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(1L, "에어맥스", 5000, "나이키", 1)
            )));

        PaymentModel payment = PaymentModel.create(order.getId(), 5000, "SAMSUNG", "1234");
        payment.markPending("TX-DONE", "SIMULATOR");
        payment.markPaid();
        payment = paymentRepository.save(payment);

        String result = recoveryService.manualConfirm(payment.getId());

        assertThat(result).contains("이미 최종 상태");
    }
}
