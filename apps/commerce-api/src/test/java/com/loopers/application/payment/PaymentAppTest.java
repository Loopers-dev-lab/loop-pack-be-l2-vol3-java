package com.loopers.application.payment;

import com.loopers.domain.payment.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApp 단위 테스트")
class PaymentAppTest {

    @InjectMocks
    private PaymentApp paymentApp;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    private static final Long ORDER_ID = 1000001L;
    private static final Long MEMBER_ID = 1L;
    private static final Long PAYMENT_ID = 1L;
    private static final String PG_TRANSACTION_ID = "txKey-001";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    private PaymentModel pendingPayment() {
        return PaymentModel.create(ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT);
    }

    private PaymentModel requestedPayment() {
        PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT);
        payment.requested(PG_TRANSACTION_ID);
        return payment;
    }

    @Nested
    @DisplayName("createPendingPayment")
    class CreatePendingPayment {

        @Test
        @DisplayName("결제 정보를 PENDING 상태로 저장하고 PaymentInfo를 반환한다")
        void success() {
            PaymentCommand command = new PaymentCommand(ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT);
            PaymentModel payment = pendingPayment();
            given(paymentService.createPending(ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT))
                .willReturn(payment);

            PaymentInfo result = paymentApp.createPendingPayment(command);

            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        }
    }

    @Nested
    @DisplayName("requestToGateway")
    class RequestToGateway {

        @Test
        @DisplayName("PG가 결제를 수락하면 REQUESTED 상태로 업데이트한 PaymentInfo를 반환한다")
        void pgAccepts() {
            PaymentModel payment = pendingPayment();
            PaymentModel requestedPayment = requestedPayment();
            given(paymentService.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.requestPayment(payment, CALLBACK_URL))
                .willReturn(new PgResult(PG_TRANSACTION_ID, PgStatus.PENDING, null, null));
            given(paymentService.updateRequested(PAYMENT_ID, PG_TRANSACTION_ID)).willReturn(requestedPayment);

            PaymentInfo result = paymentApp.requestToGateway(PAYMENT_ID, CALLBACK_URL);

            assertThat(result.status()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(result.pgTransactionId()).isEqualTo(PG_TRANSACTION_ID);
            verify(paymentService).updateRequested(PAYMENT_ID, PG_TRANSACTION_ID);
        }

        @Test
        @DisplayName("CB fallback(PG 불가) 시 PENDING 상태를 유지한 PaymentInfo를 반환하고 상태 업데이트를 하지 않는다")
        void pgUnavailable() {
            PaymentModel payment = pendingPayment();
            given(paymentService.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.requestPayment(payment, CALLBACK_URL)).willReturn(PgResult.unavailable());

            PaymentInfo result = paymentApp.requestToGateway(PAYMENT_ID, CALLBACK_URL);

            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentService, never()).updateRequested(any(), any());
        }
    }

    @Nested
    @DisplayName("handleCallback")
    class HandleCallback {

        @Test
        @DisplayName("PG SUCCESS 콜백 수신 시 COMPLETED 상태로 업데이트한다")
        void pgSuccess() {
            PaymentModel completed = requestedPayment();
            completed.complete();
            given(paymentService.updateCompleted(PG_TRANSACTION_ID, AMOUNT)).willReturn(completed);

            PaymentInfo result = paymentApp.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT);

            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(paymentService).updateCompleted(PG_TRANSACTION_ID, AMOUNT);
        }

        @Test
        @DisplayName("PG FAILED 콜백 수신 시 FAILED 상태로 업데이트한다")
        void pgFailed() {
            PaymentModel failed = requestedPayment();
            failed.fail();
            given(paymentService.updateFailed(PG_TRANSACTION_ID)).willReturn(failed);

            PaymentInfo result = paymentApp.handleCallback(PG_TRANSACTION_ID, PgStatus.FAILED, null);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentService).updateFailed(PG_TRANSACTION_ID);
            verify(paymentService, never()).updateCompleted(any(), any());
        }
    }

    @Nested
    @DisplayName("syncFromGateway")
    class SyncFromGateway {

        @Test
        @DisplayName("PG 조회 결과 SUCCESS면 COMPLETED 상태로 업데이트한다")
        void pgSuccess() {
            PaymentModel payment = requestedPayment();
            PaymentModel completed = requestedPayment();
            completed.complete();
            given(paymentService.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.getPaymentResult(PG_TRANSACTION_ID, MEMBER_ID))
                .willReturn(new PgResult(PG_TRANSACTION_ID, PgStatus.SUCCESS, "정상 승인", AMOUNT));
            given(paymentService.updateCompleted(PG_TRANSACTION_ID, AMOUNT)).willReturn(completed);

            PaymentInfo result = paymentApp.syncFromGateway(PAYMENT_ID, MEMBER_ID);

            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(paymentService).updateCompleted(PG_TRANSACTION_ID, AMOUNT);
        }

        @Test
        @DisplayName("PG 조회 불가(CB fallback) 시 현재 REQUESTED 상태를 그대로 반환한다")
        void pgUnavailable() {
            PaymentModel payment = requestedPayment();
            given(paymentService.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.getPaymentResult(PG_TRANSACTION_ID, MEMBER_ID)).willReturn(PgResult.unavailable());

            PaymentInfo result = paymentApp.syncFromGateway(PAYMENT_ID, MEMBER_ID);

            assertThat(result.status()).isEqualTo(PaymentStatus.REQUESTED);
            verify(paymentService, never()).updateCompleted(any(), any());
            verify(paymentService, never()).updateFailed(any());
        }

        @Test
        @DisplayName("PG 조회 결과 FAILED면 FAILED 상태로 업데이트한다")
        void pgFailed() {
            PaymentModel payment = requestedPayment();
            PaymentModel failed = requestedPayment();
            failed.fail();
            given(paymentService.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.getPaymentResult(PG_TRANSACTION_ID, MEMBER_ID))
                .willReturn(new PgResult(PG_TRANSACTION_ID, PgStatus.FAILED, "한도 초과", null));
            given(paymentService.updateFailed(PG_TRANSACTION_ID)).willReturn(failed);

            PaymentInfo result = paymentApp.syncFromGateway(PAYMENT_ID, MEMBER_ID);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentService).updateFailed(PG_TRANSACTION_ID);
        }

        @Test
        @DisplayName("pgTransactionId null인 결제에서 PG SUCCESS 조회 시 updateCompleted가 이미 처리된 상태라면 현재 상태를 반환한다")
        void syncByOrderId_updateCompletedConflict_returnsCurrentState() {
            PaymentModel pending = pendingPayment();
            PaymentModel requested = requestedPayment();
            PaymentModel completed = requestedPayment();
            completed.complete();

            given(paymentService.getById(PAYMENT_ID)).willReturn(pending);
            given(paymentGateway.getPaymentByOrderId(any(), any()))
                .willReturn(new PgResult(PG_TRANSACTION_ID, PgStatus.SUCCESS, "정상 승인", AMOUNT));
            given(paymentService.updateRequested(any(), any())).willReturn(requested);
            given(paymentService.updateCompleted(PG_TRANSACTION_ID, AMOUNT))
                .willThrow(new CoreException(ErrorType.BAD_REQUEST, "결제 상태 전이 불가: COMPLETED → COMPLETED"));
            given(paymentService.getByPgTransactionKey(PG_TRANSACTION_ID)).willReturn(completed);

            PaymentInfo result = paymentApp.syncFromGateway(PAYMENT_ID, MEMBER_ID);

            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(paymentService).getByPgTransactionKey(PG_TRANSACTION_ID);
        }
    }
}
