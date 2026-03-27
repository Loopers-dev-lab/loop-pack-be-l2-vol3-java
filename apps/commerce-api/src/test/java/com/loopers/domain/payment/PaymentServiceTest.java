package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private PaymentService paymentService;
    private FakePaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        paymentService = new PaymentService(paymentRepository);
    }

    @DisplayName("결제를 생성할 때, ")
    @Nested
    class CreatePending {

        @DisplayName("PENDING 상태로 생성된다.")
        @Test
        void createPending_createsPayment() {
            // act
            PaymentModel payment = paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // assert
            assertThat(payment.getId()).isNotNull();
            assertThat(payment.getOrderId()).isEqualTo(1L);
            assertThat(payment.getAmount()).isEqualTo(50000);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("이미 같은 orderId의 결제가 있으면 예외가 발생한다.")
        @Test
        void createPending_whenDuplicate_throwsException() {
            // arrange
            paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // act & assert
            assertThatThrownBy(() -> paymentService.createPending(
                    1L, 30000, CardType.KB, "****-****-****-9999"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("PG 수락으로 업데이트할 때, ")
    @Nested
    class UpdateRequested {

        @DisplayName("REQUESTED 상태로 전이되고 pgTransactionId가 저장된다.")
        @Test
        void updateRequested_setsTransactionId() {
            // arrange
            PaymentModel payment = paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // act
            paymentService.updateRequested(payment.getId(), "TX_001");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getPgTransactionId()).isEqualTo("TX_001");
        }
    }

    @DisplayName("결제를 완료할 때, ")
    @Nested
    class UpdateCompleted {

        @DisplayName("COMPLETED 상태로 전이된다.")
        @Test
        void updateCompleted_completesPayment() {
            // arrange
            PaymentModel payment = paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            paymentService.updateRequested(payment.getId(), "TX_001");

            // act
            paymentService.updateCompleted("TX_001");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getApprovedAt()).isNotNull();
        }

        @DisplayName("이미 터미널 상태면 무시한다.")
        @Test
        void updateCompleted_whenTerminal_ignores() {
            // arrange
            PaymentModel payment = paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            paymentService.updateRequested(payment.getId(), "TX_001");
            paymentService.updateCompleted("TX_001");

            // act — 중복 완료 호출
            PaymentModel result = paymentService.updateCompleted("TX_001");

            // assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    @DisplayName("결제를 실패 처리할 때, ")
    @Nested
    class UpdateFailed {

        @DisplayName("FAILED 상태로 전이되고 실패 사유가 저장된다.")
        @Test
        void updateFailed_failsWithReason() {
            // arrange
            PaymentModel payment = paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            paymentService.updateRequested(payment.getId(), "TX_001");

            // act
            paymentService.updateFailed("TX_001", "LIMIT_EXCEEDED", "한도초과");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureCode()).isEqualTo("LIMIT_EXCEEDED");
        }

        @DisplayName("이미 터미널 상태면 무시한다.")
        @Test
        void updateFailed_whenTerminal_ignores() {
            // arrange
            PaymentModel payment = paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");
            paymentService.updateRequested(payment.getId(), "TX_001");
            paymentService.updateCompleted("TX_001");

            // act — 이미 COMPLETED인데 fail 호출
            PaymentModel result = paymentService.updateFailed(
                    "TX_001", "PG_ERROR", "오류");

            // assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    @DisplayName("ID로 결제를 실패 처리할 때, ")
    @Nested
    class FailById {

        @DisplayName("Payment를 FAILED로 전이한다.")
        @Test
        void failById_setsStatusToFailed() {
            // arrange
            PaymentModel payment = paymentService.createPending(
                    1L, 50000, CardType.SAMSUNG, "****-****-****-1451");

            // act
            paymentService.failById(payment.getId());

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @DisplayName("결제를 조회할 때, ")
    @Nested
    class FindPayment {

        @DisplayName("orderId로 Payment를 조회한다.")
        @Test
        void findByOrderId_returnsPayment() {
            // arrange
            paymentService.createPending(
                    5L, 70000, CardType.HYUNDAI, "****-****-****-5555");

            // act & assert
            assertThat(paymentService.findByOrderId(5L)).isPresent();
        }

        @DisplayName("존재하지 않는 orderId면 빈 결과를 반환한다.")
        @Test
        void findByOrderId_whenNotExists_returnsEmpty() {
            assertThat(paymentService.findByOrderId(999L)).isEmpty();
        }
    }
}
