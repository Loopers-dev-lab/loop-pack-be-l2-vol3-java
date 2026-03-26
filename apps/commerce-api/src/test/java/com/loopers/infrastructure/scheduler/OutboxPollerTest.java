package com.loopers.infrastructure.scheduler;

import com.loopers.domain.payment.*;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPollerTest {

    private OutboxPollerScheduler poller;
    private FakePaymentOutboxRepository outboxRepository;
    private FakePaymentRepository paymentRepository;
    private FakePgClient pgClient;

    @BeforeEach
    void setUp() {
        outboxRepository = new FakePaymentOutboxRepository();
        paymentRepository = new FakePaymentRepository();
        pgClient = new FakePgClient("SIMULATOR");
        PgRouter pgRouter = new PgRouter(List.of(pgClient));

        poller = new OutboxPollerScheduler(outboxRepository, paymentRepository, pgRouter);
    }

    @DisplayName("U5-1: Outbox PENDING → PG 기록 없음 → PG 호출 → PROCESSED")
    @Test
    void poll_pgNoRecord_callsPgAndProcesses() {
        PaymentModel payment = paymentRepository.save(
            PaymentModel.create(1L, 10000, "SAMSUNG", "1234"));
        PaymentOutbox outbox = outboxRepository.save(
            PaymentOutbox.create(payment.getId(), 1L, "{\"orderId\":1}"));

        poller.pollOutbox();

        assertThat(outbox.getStatus()).isEqualTo(PaymentOutboxStatus.PROCESSED);
        PaymentModel updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(updated.getTransactionKey()).isNotNull();
    }

    @DisplayName("U5-2: Outbox PENDING → Payment 이미 PAID → PROCESSED (PG 호출 없이)")
    @Test
    void poll_paymentAlreadyPaid_marksProcessed() {
        PaymentModel payment = paymentRepository.save(
            PaymentModel.create(1L, 10000, "SAMSUNG", "1234"));
        payment.markPending("TX-001", "SIMULATOR");
        payment.markPaid();
        paymentRepository.save(payment);

        PaymentOutbox outbox = outboxRepository.save(
            PaymentOutbox.create(payment.getId(), 1L, "{\"orderId\":1}"));

        poller.pollOutbox();

        assertThat(outbox.getStatus()).isEqualTo(PaymentOutboxStatus.PROCESSED);
        assertThat(pgClient.getCallCount()).isZero();
    }

    @DisplayName("U5-3: Outbox retry 3회 초과 → FAILED")
    @Test
    void poll_retryExceeded_marksFailed() {
        PaymentModel payment = paymentRepository.save(
            PaymentModel.create(1L, 10000, "SAMSUNG", "1234"));
        PaymentOutbox outbox = outboxRepository.save(
            PaymentOutbox.create(payment.getId(), 1L, "{\"orderId\":1}"));

        pgClient.setShouldFail(true);

        // 4회 폴링 → retryCount 3 초과 시 FAILED
        for (int i = 0; i < 4; i++) {
            poller.pollOutbox();
        }

        assertThat(outbox.getStatus()).isEqualTo(PaymentOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isGreaterThan(3);
    }
}
