package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
@SpringBootTest
class PaymentEventListenerTest {

    private final PaymentService paymentService;
    private final PaymentJpaRepository paymentJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final ApplicationEvents applicationEvents;

    @Autowired
    public PaymentEventListenerTest(
        PaymentService paymentService,
        PaymentJpaRepository paymentJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        ApplicationEvents applicationEvents
    ) {
        this.paymentService = paymentService;
        this.paymentJpaRepository = paymentJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.applicationEvents = applicationEvents;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Payment savedPendingPayment() {
        return paymentJpaRepository.save(
            new Payment(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 100000L, PaymentStatus.PENDING, null)
        );
    }

    @DisplayName("handleCallback")
    @Nested
    class HandleCallback {

        @DisplayName("성공 콜백이 오면 PaymentCompletedEvent가 발행된다.")
        @Test
        void publishesPaymentCompletedEvent_whenCallbackSucceeds() {
            // arrange
            Payment payment = savedPendingPayment();

            // act
            paymentService.handleCallback(payment.getOrderId(), "tx-001", true);

            // assert
            assertThat(applicationEvents.stream(PaymentCompletedEvent.class))
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.orderId()).isEqualTo(payment.getOrderId());
                    assertThat(event.memberId()).isEqualTo(payment.getMemberId());
                    assertThat(event.amount()).isEqualTo(payment.getAmount());
                });
        }

        @DisplayName("실패 콜백이 오면 PaymentCompletedEvent가 발행되지 않는다.")
        @Test
        void doesNotPublishPaymentCompletedEvent_whenCallbackFails() {
            // arrange
            Payment payment = savedPendingPayment();

            // act
            paymentService.handleCallback(payment.getOrderId(), "tx-002", false);

            // assert
            assertThat(applicationEvents.stream(PaymentCompletedEvent.class)).isEmpty();
        }
    }
}