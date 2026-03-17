package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
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
        void 유효한_값이면_PENDING_상태로_생성된다() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertAll(
                    () -> assertThat(payment.getId()).isNotNull(),
                    () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                    () -> assertThat(payment.getUserId()).isEqualTo(100L),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("50000"))
            );
        }
    }

    @Nested
    class 상태_변경_IN_PROGRESS {

        @Test
        void PENDING에서_IN_PROGRESS로_변경된다() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            paymentService.markInProgress(payment.getId(), "20250317:TR:abc123");

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                    () -> assertThat(updated.getTransactionKey()).isEqualTo("20250317:TR:abc123")
            );
        }
    }

    @Nested
    class 상태_변경_FAILED {

        @Test
        void PENDING에서_FAILED로_변경된다() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            paymentService.markFailed(payment.getId(), "PG 요청 실패");

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(updated.getFailReason()).isEqualTo("PG 요청 실패")
            );
        }
    }

    @Nested
    class 결제_조회 {

        @Test
        void ID로_조회하면_결제_정보를_반환한다() {
            Payment created = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

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
    class 상태_변경_SUCCEEDED {

        @Test
        void PENDING에서_SUCCEEDED로_변경된다() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            paymentService.markSucceeded(payment.getId(), "20250317:TR:abc123");

            Payment updated = paymentService.getPayment(payment.getId());
            assertAll(
                    () -> assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED),
                    () -> assertThat(updated.getTransactionKey()).isEqualTo("20250317:TR:abc123")
            );
        }

        @Test
        void IN_PROGRESS에서_SUCCEEDED로_변경된다() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            paymentService.markInProgress(payment.getId(), "20250317:TR:abc123");

            paymentService.markSucceeded(payment.getId(), "20250317:TR:abc123");

            Payment updated = paymentService.getPayment(payment.getId());
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
    }

    @Nested
    class transactionKey_조회 {

        @Test
        void 존재하는_transactionKey이면_결제를_반환한다() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            paymentService.markInProgress(payment.getId(), "20250317:TR:abc123");

            Optional<Payment> found = paymentService.getPaymentByTransactionKey("20250317:TR:abc123");

            assertAll(
                    () -> assertThat(found).isPresent(),
                    () -> assertThat(found.get().getId()).isEqualTo(payment.getId())
            );
        }

        @Test
        void 존재하지_않는_transactionKey이면_빈_Optional을_반환한다() {
            Optional<Payment> found = paymentService.getPaymentByTransactionKey("nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    class 주문별_최신_결제_조회 {

        @Test
        void 결제가_있으면_최신_결제를_반환한다() {
            Payment first = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            first.markFailed("첫 번째 실패");
            paymentRepository.save(first);

            Payment second = paymentService.createPayment(1L, 100L, CardType.KB, "9999-8888-7777-6666", new BigDecimal("50000"));

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
        void PENDING_상태의_결제가_있으면_true() {
            paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThat(paymentService.existsActivePayment(1L)).isTrue();
        }

        @Test
        void SUCCEEDED_상태의_결제가_있으면_true() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded("20250317:TR:abc123");
            paymentRepository.save(payment);

            assertThat(paymentService.existsActivePayment(1L)).isTrue();
        }

        @Test
        void FAILED_상태의_결제만_있으면_false() {
            Payment payment = paymentService.createPayment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markFailed("한도초과");
            paymentRepository.save(payment);

            assertThat(paymentService.existsActivePayment(1L)).isFalse();
        }

        @Test
        void 결제가_없으면_false() {
            assertThat(paymentService.existsActivePayment(999L)).isFalse();
        }
    }
}
