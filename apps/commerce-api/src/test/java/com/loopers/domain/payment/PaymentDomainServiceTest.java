package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentDomainServiceTest {

    private FakePaymentRepository paymentRepository;
    private PaymentDomainService paymentDomainService;

    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        paymentDomainService = new PaymentDomainService(paymentRepository);
    }

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class CreatePayment {

        @DisplayName("PENDING 상태로 생성되고 저장된다.")
        @Test
        void createsPaymentWithPendingStatus() {
            Payment payment = paymentDomainService.createPayment(
                1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000
            );

            assertAll(
                () -> assertThat(payment.getId()).isNotNull(),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                () -> assertThat(payment.getAmount()).isEqualTo(50000)
            );
        }
    }

    @DisplayName("결제를 조회할 때, ")
    @Nested
    class GetPayment {

        @DisplayName("존재하지 않는 transactionKey로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTransactionKeyNotExists() {
            CoreException result = assertThrows(CoreException.class,
                () -> paymentDomainService.getByTransactionKey("nonexistent"));
            assertThat(result.getErrorType().getStatusCode()).isEqualTo(404);
        }

        @DisplayName("transactionKey로 조회할 수 있다.")
        @Test
        void findsByTransactionKey() {
            Payment payment = paymentDomainService.createPayment(
                1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000
            );
            payment.markInProgress("20250316:TR:abc123");

            Payment found = paymentDomainService.getByTransactionKey("20250316:TR:abc123");
            assertThat(found.getTransactionKey()).isEqualTo("20250316:TR:abc123");
        }
    }

    @DisplayName("PENDING/IN_PROGRESS 결제를 조회할 때, ")
    @Nested
    class GetPendingPayments {

        @DisplayName("PENDING과 IN_PROGRESS 상태만 반환한다.")
        @Test
        void returnsOnlyPendingAndInProgress() {
            Payment pending = paymentDomainService.createPayment(
                1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000
            );

            Payment inProgress = paymentDomainService.createPayment(
                2L, 100L, CardType.KB, "1234-5678-9012-3456", 20000
            );
            inProgress.markInProgress("20250316:TR:abc123");

            Payment paid = paymentDomainService.createPayment(
                3L, 100L, CardType.HYUNDAI, "1234-5678-9012-3456", 30000
            );
            paid.markInProgress("20250316:TR:def456");
            paid.markPaid();

            List<Payment> result = paymentDomainService.getPendingAndInProgressPayments();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Payment::getStatus)
                .containsExactlyInAnyOrder(PaymentStatus.PENDING, PaymentStatus.IN_PROGRESS);
        }
    }
}
