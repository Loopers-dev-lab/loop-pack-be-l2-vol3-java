package com.loopers.infrastructure.scheduler;

import com.loopers.application.payment.PaymentRecoveryService;
import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.*;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackDlqSchedulerTest {

    private CallbackDlqScheduler dlqScheduler;
    private FakeCallbackInboxRepository callbackInboxRepository;
    private FakePaymentRepository paymentRepository;
    private FakeOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        callbackInboxRepository = new FakeCallbackInboxRepository();
        paymentRepository = new FakePaymentRepository();
        orderRepository = new FakeOrderRepository();
        FakeProductRepository productRepository = new FakeProductRepository();
        FakeCouponIssueRepository couponIssueRepository = new FakeCouponIssueRepository();
        FakeStockReservationRedisRepository stockRedisRepository = new FakeStockReservationRedisRepository();
        FakePgClient pgClient = new FakePgClient("SIMULATOR");
        PgRouter pgRouter = new PgRouter(List.of(pgClient));

        PaymentRecoveryService recoveryService = new PaymentRecoveryService(
            paymentRepository, callbackInboxRepository, orderRepository,
            productRepository, couponIssueRepository, stockRedisRepository, pgRouter);

        dlqScheduler = new CallbackDlqScheduler(callbackInboxRepository, recoveryService);
    }

    @DisplayName("U5-8: RECEIVED(30초 전) → 재처리 → PROCESSED")
    @Test
    void reprocess_oldReceived_processed() throws Exception {
        // Payment 준비
        Order order = orderRepository.save(
            Order.create(100L, List.of(
                new Order.ItemSnapshot(1L, "에어맥스", 5000, "나이키", 1)
            )));
        PaymentModel payment = PaymentModel.create(order.getId(), 5000, "SAMSUNG", "1234");
        payment.markPending("TX-DLQ-001", "SIMULATOR");
        paymentRepository.save(payment);

        // RECEIVED 상태 Inbox (30초 전)
        CallbackInbox inbox = callbackInboxRepository.save(
            CallbackInbox.create("TX-DLQ-001", order.getId(), "SUCCESS", "{}"));
        setCreatedAt(inbox, ZonedDateTime.now().minusSeconds(35));

        dlqScheduler.reprocessFailedCallbacks();

        // 재처리 결과: 새 Inbox가 생성되고 PROCESSED
        List<CallbackInbox> inboxes = callbackInboxRepository.findAllByTransactionKey("TX-DLQ-001");
        boolean hasProcessed = inboxes.stream()
            .anyMatch(i -> i.getStatus() == CallbackInboxStatus.PROCESSED);
        assertThat(hasProcessed).isTrue();
    }

    @DisplayName("최근 RECEIVED(10초 전) → 재처리 대상 아님")
    @Test
    void reprocess_recentReceived_notProcessed() throws Exception {
        CallbackInbox inbox = callbackInboxRepository.save(
            CallbackInbox.create("TX-RECENT", 1L, "SUCCESS", "{}"));
        setCreatedAt(inbox, ZonedDateTime.now().minusSeconds(10));

        dlqScheduler.reprocessFailedCallbacks();

        // 상태 변경 없음
        assertThat(inbox.getStatus()).isEqualTo(CallbackInboxStatus.RECEIVED);
    }

    private void setCreatedAt(Object entity, ZonedDateTime createdAt) throws Exception {
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(entity, createdAt);
    }
}
