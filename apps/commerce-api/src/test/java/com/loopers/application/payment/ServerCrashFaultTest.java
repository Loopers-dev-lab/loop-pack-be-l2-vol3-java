package com.loopers.application.payment;

import com.loopers.domain.payment.*;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgRouter;
import com.loopers.infrastructure.scheduler.OutboxPollerScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F7-2: 서버 크래시 → Outbox 복구 시나리오.
 *
 * <p>TX-1 커밋 후 PG 호출 전에 서버가 죽은 상황.
 * Payment(REQUESTED) + Outbox(PENDING)만 DB에 남아있고, PG 호출은 안 된 상태.
 * Outbox 폴러가 5초마다 스캔하여 PG 호출을 재시도.</p>
 *
 * @see <a href="05-payment-resilience.md §13.4">Outbox 폴러</a>
 */
class ServerCrashFaultTest {

    private OutboxPollerScheduler outboxPoller;
    private FakePaymentOutboxRepository outboxRepository;
    private FakePaymentRepository paymentRepository;
    private FakePgClient pgClient;

    @BeforeEach
    void setUp() {
        outboxRepository = new FakePaymentOutboxRepository();
        paymentRepository = new FakePaymentRepository();
        pgClient = new FakePgClient("SIMULATOR");
        PgRouter pgRouter = new PgRouter(List.of(pgClient));

        outboxPoller = new OutboxPollerScheduler(outboxRepository, paymentRepository, pgRouter);
    }

    @DisplayName("F7-2: TX-1 커밋 → PG 호출 안 됨 → Outbox 폴러가 PG 호출 → PENDING")
    @Test
    void serverCrash_outboxRecovery_pgCalled() {
        // Given: TX-1 커밋 상태 (Payment REQUESTED + Outbox PENDING)
        // 서버 크래시로 PG 호출이 안 된 상황을 시뮬레이션
        PaymentModel payment = paymentRepository.save(
            PaymentModel.create(1L, 10000, "SAMSUNG", "1234"));
        PaymentOutbox outbox = outboxRepository.save(
            PaymentOutbox.create(payment.getId(), 1L, "{\"orderId\":1}"));

        // Payment는 REQUESTED 상태 (PG 호출 안 됨)
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(outbox.getStatus()).isEqualTo(PaymentOutboxStatus.PENDING);
        assertThat(pgClient.getCallCount()).isZero();

        // When: Outbox 폴러 실행 (서버 재기동 후 5초 내)
        outboxPoller.pollOutbox();

        // Then: PG 호출됨 → Payment PENDING → Outbox PROCESSED
        assertThat(pgClient.getCallCount()).isEqualTo(1);
        PaymentModel updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(updatedPayment.getTransactionKey()).isNotNull();
        assertThat(outbox.getStatus()).isEqualTo(PaymentOutboxStatus.PROCESSED);
    }

    @DisplayName("F7-2 변형: PG도 장애 → Outbox 재시도 3회 → FAILED")
    @Test
    void serverCrash_pgAlsoDown_retryExhausted() {
        // Given: TX-1 커밋 + PG 장애
        PaymentModel payment = paymentRepository.save(
            PaymentModel.create(1L, 10000, "SAMSUNG", "1234"));
        PaymentOutbox outbox = outboxRepository.save(
            PaymentOutbox.create(payment.getId(), 1L, "{\"orderId\":1}"));

        pgClient.setShouldFail(true); // PG 장애

        // When: Outbox 폴러 4회 실행 (retry 3회 초과)
        for (int i = 0; i < 4; i++) {
            outboxPoller.pollOutbox();
        }

        // Then: Outbox FAILED (운영 알림 대상)
        assertThat(outbox.getStatus()).isEqualTo(PaymentOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isGreaterThan(3);
    }
}
