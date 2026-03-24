package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("PaymentAppService 통합 테스트")
class PaymentAppServiceTest {

    @Autowired
    private PaymentAppService paymentAppService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Payment createAndSavePayment(Long orderId, Long userId) {
        return paymentAppService.createPayment(orderId, userId, "VISA", "4111111111111111", Money.of(50000L));
    }

    @Nested
    @DisplayName("결제 생성")
    class CreatePaymentTest {

        @Test
        @DisplayName("결제를 생성하면 PENDING 상태로 저장된다")
        void createPayment_success() {
            // when
            Payment payment = createAndSavePayment(1L, 100L);

            // then
            assertThat(payment.getId()).isNotNull();
            assertThat(payment.getOrderId()).isEqualTo(1L);
            assertThat(payment.getUserId()).isEqualTo(100L);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getCardType()).isEqualTo("VISA");
            assertThat(payment.getAmount()).isEqualTo(Money.of(50000L));
        }

        @Test
        @DisplayName("동일 주문에 PENDING 결제가 존재하면 중복 생성할 수 없다")
        void createPayment_duplicatePending_throwsConflict() {
            // given
            createAndSavePayment(1L, 100L);

            // when & then
            assertThatThrownBy(() -> createAndSavePayment(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이미 진행 중이거나 완료된 결제가 존재합니다");
        }

        @Test
        @DisplayName("동일 주문에 SUCCESS 결제가 존재하면 중복 생성할 수 없다")
        void createPayment_duplicateSuccess_throwsConflict() {
            // given
            Payment payment = createAndSavePayment(1L, 100L);
            paymentAppService.completePayment(payment.getId(), "txn-123", "결제 성공");

            // when & then
            assertThatThrownBy(() -> createAndSavePayment(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이미 진행 중이거나 완료된 결제가 존재합니다");
        }

        @Test
        @DisplayName("동일 주문의 이전 결제가 FAIL이면 새로 생성할 수 있다")
        void createPayment_afterFail_success() {
            // given
            Payment failed = createAndSavePayment(1L, 100L);
            paymentAppService.failPayment(failed.getId(), "카드 한도 초과");

            // when
            Payment newPayment = createAndSavePayment(1L, 100L);

            // then
            assertThat(newPayment.getId()).isNotEqualTo(failed.getId());
            assertThat(newPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("결제 완료")
    class CompletePaymentTest {

        @Test
        @DisplayName("PENDING 결제를 완료하면 SUCCESS 상태가 된다")
        void completePayment_success() {
            // given
            Payment payment = createAndSavePayment(1L, 100L);

            // when
            Payment completed = paymentAppService.completePayment(payment.getId(), "txn-123", "결제 성공");

            // then
            assertThat(completed.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(completed.getTransactionId()).isEqualTo("txn-123");
            assertThat(completed.getPgResponseMessage()).isEqualTo("결제 성공");
        }

        @Test
        @DisplayName("존재하지 않는 결제를 완료하면 예외가 발생한다")
        void completePayment_notFound() {
            assertThatThrownBy(() -> paymentAppService.completePayment(999L, "txn-123", "결제 성공"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("결제 실패")
    class FailPaymentTest {

        @Test
        @DisplayName("PENDING 결제를 실패 처리하면 FAIL 상태가 된다")
        void failPayment_success() {
            // given
            Payment payment = createAndSavePayment(1L, 100L);

            // when
            Payment failed = paymentAppService.failPayment(payment.getId(), "카드 한도 초과");

            // then
            assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAIL);
            assertThat(failed.getPgResponseMessage()).isEqualTo("카드 한도 초과");
        }
    }

    @Nested
    @DisplayName("결제 조회")
    class GetPaymentTest {

        @Test
        @DisplayName("ID로 결제를 조회할 수 있다")
        void getById_success() {
            // given
            Payment payment = createAndSavePayment(1L, 100L);

            // when
            Payment found = paymentAppService.getById(payment.getId());

            // then
            assertThat(found.getId()).isEqualTo(payment.getId());
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 예외가 발생한다")
        void getById_notFound() {
            assertThatThrownBy(() -> paymentAppService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("주문 ID로 활성 결제를 조회할 수 있다")
        void getByOrderIdAndActiveStatus_success() {
            // given
            Payment payment = createAndSavePayment(1L, 100L);

            // when
            Payment found = paymentAppService.getByOrderIdAndActiveStatus(1L);

            // then
            assertThat(found.getId()).isEqualTo(payment.getId());
            assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("활성 결제가 없는 주문 ID로 조회하면 예외가 발생한다")
        void getByOrderIdAndActiveStatus_notFound() {
            assertThatThrownBy(() -> paymentAppService.getByOrderIdAndActiveStatus(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("해당 주문의 결제를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("PENDING 상태의 결제 목록을 조회할 수 있다")
        void getPendingPayments_success() {
            // given
            createAndSavePayment(1L, 100L);
            createAndSavePayment(2L, 100L);
            Payment completed = createAndSavePayment(3L, 100L);
            paymentAppService.completePayment(completed.getId(), "txn-123", "성공");

            // when
            List<Payment> pendingPayments = paymentAppService.getPendingPayments();

            // then
            assertThat(pendingPayments).hasSize(2);
            assertThat(pendingPayments).allMatch(p -> p.getStatus() == PaymentStatus.PENDING);
        }
    }
}
