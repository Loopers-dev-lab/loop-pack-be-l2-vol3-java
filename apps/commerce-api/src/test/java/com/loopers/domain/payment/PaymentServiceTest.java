package com.loopers.domain.payment;

import com.loopers.domain.payment.vo.RefOrderId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private static final Long ORDER_ID = 1L;
    private static final Long MEMBER_ID = 1L;
    private static final CardType CARD_TYPE = CardType.SAMSUNG;
    private static final String CARD_NUMBER = "1234-5678-9012-3456";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");
    private static final String PG_TRANSACTION_KEY = "20260315:TR:cb62da";

    private PaymentModel pendingPayment() {
        return PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);
    }

    private PaymentModel requestedPayment() {
        PaymentModel payment = pendingPayment();
        payment.requested(PG_TRANSACTION_KEY);
        return payment;
    }

    @DisplayName("결제 생성 (PENDING) 시,")
    @Nested
    class CreatePending {

        @Test
        @DisplayName("정상 입력으로 PENDING 상태 결제가 저장된다")
        void createPending_success() {
            PaymentModel expected = pendingPayment();
            given(paymentRepository.findByRefOrderId(any(RefOrderId.class))).willReturn(Optional.empty());
            given(paymentRepository.save(any(PaymentModel.class))).willReturn(expected);

            PaymentModel result = paymentService.createPending(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getRefOrderId().value()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("동일한 orderId로 이미 결제가 존재하면 CONFLICT 예외가 발생한다")
        void createPending_duplicateOrder_throwsConflict() {
            given(paymentRepository.findByRefOrderId(any(RefOrderId.class))).willReturn(Optional.of(pendingPayment()));

            assertThatThrownBy(() -> paymentService.createPending(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @DisplayName("REQUESTED 상태로 업데이트 시,")
    @Nested
    class UpdateRequested {

        @Test
        @DisplayName("PENDING 결제에 pgTransactionKey 저장 후 REQUESTED 상태가 된다")
        void updateRequested_success() {
            PaymentModel payment = pendingPayment();
            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.updateRequested(1L, PG_TRANSACTION_KEY);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(result.getPgTransactionId()).isEqualTo(PG_TRANSACTION_KEY);
        }

        @Test
        @DisplayName("존재하지 않는 paymentId이면 예외가 발생한다")
        void updateRequested_notFound_throwsException() {
            given(paymentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.updateRequested(999L, PG_TRANSACTION_KEY))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("COMPLETED 상태로 업데이트 시,")
    @Nested
    class UpdateCompleted {

        @Test
        @DisplayName("PG 금액과 저장 금액이 일치하면 COMPLETED 상태가 된다")
        void updateCompleted_amountMatch_success() {
            PaymentModel payment = requestedPayment();
            given(paymentRepository.findByPgTransactionId(PG_TRANSACTION_KEY)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.updateCompleted(PG_TRANSACTION_KEY, AMOUNT);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("PG 금액과 저장 금액이 다르면 이상 거래로 감지하여 FAILED 상태가 된다")
        void updateCompleted_amountMismatch_becomesFailed() {
            PaymentModel payment = requestedPayment();
            given(paymentRepository.findByPgTransactionId(PG_TRANSACTION_KEY)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.updateCompleted(PG_TRANSACTION_KEY, new BigDecimal("9999"));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("존재하지 않는 pgTransactionKey이면 예외가 발생한다")
        void updateCompleted_notFound_throwsException() {
            given(paymentRepository.findByPgTransactionId("NOT_EXIST")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.updateCompleted("NOT_EXIST", AMOUNT))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("FAILED 상태로 업데이트 시,")
    @Nested
    class UpdateFailed {

        @Test
        @DisplayName("REQUESTED 결제가 FAILED 상태가 된다")
        void updateFailed_fromRequested_success() {
            PaymentModel payment = requestedPayment();
            given(paymentRepository.findByPgTransactionId(PG_TRANSACTION_KEY)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.updateFailed(PG_TRANSACTION_KEY);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("존재하지 않는 pgTransactionKey이면 예외가 발생한다")
        void updateFailed_notFound_throwsException() {
            given(paymentRepository.findByPgTransactionId("NOT_EXIST")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.updateFailed("NOT_EXIST"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제 조회 시,")
    @Nested
    class Get {

        @Test
        @DisplayName("존재하는 paymentId로 조회 성공")
        void getById_success() {
            PaymentModel payment = pendingPayment();
            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.getById(1L);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 paymentId이면 예외가 발생한다")
        void getById_notFound_throwsException() {
            given(paymentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getById(999L))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("존재하는 pgTransactionKey로 조회 성공")
        void getByPgTransactionKey_success() {
            PaymentModel payment = requestedPayment();
            given(paymentRepository.findByPgTransactionId(PG_TRANSACTION_KEY)).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.getByPgTransactionKey(PG_TRANSACTION_KEY);

            assertThat(result.getPgTransactionId()).isEqualTo(PG_TRANSACTION_KEY);
        }

        @Test
        @DisplayName("존재하는 orderId로 조회 성공")
        void getByRefOrderId_success() {
            PaymentModel payment = pendingPayment();
            given(paymentRepository.findByRefOrderId(any(RefOrderId.class))).willReturn(Optional.of(payment));

            PaymentModel result = paymentService.getByRefOrderId(ORDER_ID);

            assertThat(result.getRefOrderId().value()).isEqualTo(ORDER_ID);
        }
    }
}
