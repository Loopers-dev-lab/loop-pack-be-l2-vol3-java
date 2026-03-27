package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 결제_생성 {

        @Test
        void 유효한_값이면_REQUESTED_상태로_생성된다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            assertAll(
                    () -> assertThat(payment.getId()).isNotNull(),
                    () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                    () -> assertThat(payment.getUserId()).isEqualTo(100L),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
                    () -> assertThat(payment.getPaymentKey()).isNotNull(),
                    () -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("50000"))
            );
        }
    }

    @Nested
    class 상태_변경_SUCCEEDED {

        @Test
        void REQUESTED에서_SUCCEEDED로_변경된다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            paymentService.markSucceeded(payment.getId());

            Payment updated = paymentService.getPayment(payment.getId());
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @Test
        void REQUESTED에서_멱등하게_SUCCEEDED로_변경된다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            boolean result = paymentService.markSucceededIfRequested(payment.getId());

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(result).isTrue(),
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED)
            );
        }

        @Test
        void 이미_SUCCEEDED_상태이면_false를_반환한다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            paymentService.markSucceededIfRequested(payment.getId());

            boolean result = paymentService.markSucceededIfRequested(payment.getId());

            assertThat(result).isFalse();
        }
    }

    @Nested
    class 상태_변경_FAILED {

        @Test
        void REQUESTED에서_FAILED로_변경된다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            paymentService.markFailed(payment.getId(), "PG 요청 실패");

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(updated.getFailReason()).isEqualTo("PG 요청 실패")
            );
        }

        @Test
        void REQUESTED에서_멱등하게_FAILED로_변경된다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            boolean result = paymentService.markFailedIfRequested(payment.getId(), "PG 실패");

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(result).isTrue(),
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(updated.getFailReason()).isEqualTo("PG 실패")
            );
        }

        @Test
        void 이미_FAILED_상태이면_false를_반환한다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            paymentService.markFailedIfRequested(payment.getId(), "PG 실패");

            boolean result = paymentService.markFailedIfRequested(payment.getId(), "PG 실패");

            assertThat(result).isFalse();
        }
    }

    @Nested
    class 상태_변경_CANCEL_REQUESTED {

        @Test
        void SUCCEEDED에서_CANCEL_REQUESTED로_변경된다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            paymentService.markSucceeded(payment.getId());

            paymentService.markCancelRequested(payment.getId(), "단순 변심");

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED),
                    () -> assertThat(updated.getCancelReason()).isEqualTo("단순 변심")
            );
        }
    }

    @Nested
    class 상태_변경_CANCELED {

        @Test
        void CANCEL_REQUESTED에서_CANCELED로_변경된다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            paymentService.markSucceeded(payment.getId());
            paymentService.markCancelRequested(payment.getId(), "단순 변심");

            boolean result = paymentService.markCanceledIfRequested(payment.getId());

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(result).isTrue(),
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CANCELED),
                    () -> assertThat(updated.getCancelReason()).isEqualTo("단순 변심"),
                    () -> assertThat(updated.getCanceledAt()).isNotNull()
            );
        }

        @Test
        void 이미_CANCELED_상태이면_false를_반환한다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            paymentService.markSucceeded(payment.getId());
            paymentService.markCancelRequested(payment.getId(), "단순 변심");
            paymentService.markCanceledIfRequested(payment.getId());

            boolean result = paymentService.markCanceledIfRequested(payment.getId());

            assertThat(result).isFalse();
        }
    }

    @Nested
    class 결제_조회 {

        @Test
        void ID로_조회하면_결제_정보를_반환한다() {
            Payment created = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            Payment payment = paymentService.getPayment(created.getId());

            assertThat(payment.getId()).isEqualTo(created.getId());
        }

        @Test
        void 존재하지_않는_결제이면_예외() {
            assertThatThrownBy(() -> paymentService.getPayment(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    class paymentKey_조회 {

        @Test
        void 존재하는_paymentKey이면_결제를_반환한다() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            Optional<Payment> found = paymentService.getPaymentByPaymentKey(payment.getPaymentKey());

            assertAll(
                    () -> assertThat(found).isPresent(),
                    () -> assertThat(found.get().getId()).isEqualTo(payment.getId())
            );
        }

        @Test
        void 존재하지_않는_paymentKey이면_빈_Optional을_반환한다() {
            Optional<Payment> found = paymentService.getPaymentByPaymentKey("nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    class 주문별_최신_결제_조회 {

        @Test
        void 결제가_있으면_최신_결제를_반환한다() {
            Payment first = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            first.markFailed("첫 번째 실패");
            paymentRepository.save(first);

            Payment second = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.KB, "9999-8888-7777-6666", new BigDecimal("50000")));

            Optional<Payment> found = paymentService.getLatestPaymentByOrderId(1L);

            assertAll(
                    () -> assertThat(found).isPresent(),
                    () -> assertThat(found.get().getId()).isEqualTo(second.getId())
            );
        }

        @Test
        void 결제가_없으면_빈_Optional을_반환한다() {
            Optional<Payment> found = paymentService.getLatestPaymentByOrderId(999L);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    class 활성_결제_존재_확인 {

        @Test
        void REQUESTED_상태의_결제가_있으면_true() {
            paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

            assertThat(paymentService.existsActivePayment(1L)).isTrue();
        }

        @Test
        void SUCCEEDED_상태의_결제가_있으면_true() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            payment.markSucceeded();
            paymentRepository.save(payment);

            assertThat(paymentService.existsActivePayment(1L)).isTrue();
        }

        @Test
        void FAILED_상태의_결제만_있으면_false() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            payment.markFailed("한도초과");
            paymentRepository.save(payment);

            assertThat(paymentService.existsActivePayment(1L)).isFalse();
        }

        @Test
        void CANCELED_상태의_결제만_있으면_false() {
            Payment payment = paymentService.createPayment(PaymentCommand.Create.of(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
            payment.markSucceeded();
            payment.markCancelRequested("변심");
            payment.markCanceled();
            paymentRepository.save(payment);

            assertThat(paymentService.existsActivePayment(1L)).isFalse();
        }

        @Test
        void 결제가_없으면_false() {
            assertThat(paymentService.existsActivePayment(999L)).isFalse();
        }
    }
}
