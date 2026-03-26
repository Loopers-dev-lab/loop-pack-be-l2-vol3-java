package com.loopers.infrastructure.pg.toss;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toss Sandbox PG 동기 결제 테스트.
 *
 * <p>Toss는 동기 PG — requestPayment() 응답이 SUCCESS/FAILED 즉시 반환.
 * 콜백 대기 없이 PaymentFacade에서 바로 최종 상태 전이.</p>
 */
class TossSandboxPgClientTest {

    private PaymentFacade paymentFacade;
    private FakePaymentRepository paymentRepository;
    private FakeOrderRepository orderRepository;
    private FakePaymentOutboxRepository outboxRepository;
    private FakePgClient tossPgClient;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = new FakePaymentRepository();
        orderRepository = new FakeOrderRepository();
        outboxRepository = new FakePaymentOutboxRepository();
        tossPgClient = new FakePgClient("TOSS");
        PgRouter pgRouter = new PgRouter(List.of(tossPgClient));

        paymentFacade = new PaymentFacade(paymentRepository, orderRepository, pgRouter, outboxRepository);

        setField(paymentFacade, "callbackUrl", "http://test/callback");
        setField(paymentFacade, "maxRetryAttempts", 3);
        setField(paymentFacade, "initialWaitMs", 0L);
        setField(paymentFacade, "backoffMultiplier", 2);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Order createTestOrder() {
        FakeBrandRepository brandRepository = new FakeBrandRepository();
        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
        return orderRepository.save(Order.create(100L, List.of(
            new Order.ItemSnapshot(1L, "에어맥스", 5000, brand.getName(), 1)
        )));
    }

    @DisplayName("U6-1: Toss SUCCESS → Payment PAID 즉시 (콜백 불필요)")
    @Test
    void tossSuccess_paymentPaidImmediately() {
        tossPgClient.setResponseStatus("SUCCESS");
        Order order = createTestOrder();

        PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
            order.getId(), "SAMSUNG", "1234", 5000);

        // Payment → PAID 즉시
        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.transactionKey()).isNotNull();

        PaymentModel payment = paymentRepository.findById(result.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPgProvider()).isEqualTo("TOSS");

        // Order → PAID 즉시 (콜백 대기 불필요)
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @DisplayName("U6-2: Toss FAILED → Payment FAILED 즉시")
    @Test
    void tossFailed_paymentFailedImmediately() {
        tossPgClient.setResponseStatus("FAILED");
        Order order = createTestOrder();

        PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
            order.getId(), "SAMSUNG", "1234", 5000);

        // Payment → FAILED 즉시
        assertThat(result.status()).isEqualTo("FAILED");

        PaymentModel payment = paymentRepository.findById(result.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
