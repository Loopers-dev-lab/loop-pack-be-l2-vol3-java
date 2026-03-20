package com.loopers.batch;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentPollingScheduler 테스트")
class PaymentPollingSchedulerTest {

    @Mock PaymentService paymentService;
    @Mock PaymentFacade paymentFacade;
    @Mock PaymentGateway paymentGateway;

    @InjectMocks PaymentPollingScheduler scheduler;

    @Nested
    @DisplayName("transactionKey 있는 결제 폴링")
    class WithTransactionKey {

        @Test
        @DisplayName("콜백 유실 후 폴링으로 상태를 복구한다")
        void pollPendingPayments_WithLostCallback_ShouldRecoverStatus() {
            PaymentModel payment = createPaymentWithTransactionKey("TXN-001");
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(payment));
            given(paymentGateway.getPaymentStatus("TXN-001"))
                    .willReturn(new GatewayPaymentResult("TXN-001", true, "SUCCESS", null));

            scheduler.pollPendingPayments();

            verify(paymentFacade).handleCallback("TXN-001", "SUCCESS", null);
        }

        @Test
        @DisplayName("PG 조회 실패 시 다른 건은 정상 처리한다")
        void pollPendingPayments_WithPgQueryFailure_ShouldContinueOthers() {
            PaymentModel failPayment = createPaymentWithTransactionKey("TXN-FAIL");
            PaymentModel okPayment = createPaymentWithTransactionKey("TXN-OK");
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(failPayment, okPayment));
            given(paymentGateway.getPaymentStatus("TXN-FAIL"))
                    .willThrow(new RuntimeException("PG 연결 실패"));
            given(paymentGateway.getPaymentStatus("TXN-OK"))
                    .willReturn(new GatewayPaymentResult("TXN-OK", false, "FAILED", "잔액 부족"));

            scheduler.pollPendingPayments();

            verify(paymentFacade, never()).handleCallback(eq("TXN-FAIL"), any(), any());
            verify(paymentFacade).handleCallback("TXN-OK", "FAILED", "잔액 부족");
        }

        @Test
        @DisplayName("PG 상태가 PENDING이면 상태 반영하지 않는다")
        void pollPendingPayments_WithPendingStatus_ShouldNotUpdate() {
            PaymentModel payment = createPaymentWithTransactionKey("TXN-PENDING");
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(payment));
            given(paymentGateway.getPaymentStatus("TXN-PENDING"))
                    .willReturn(new GatewayPaymentResult("TXN-PENDING", false, "PENDING", null));

            scheduler.pollPendingPayments();

            verify(paymentFacade, never()).handleCallback(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Phase C: transactionKey 없는 고아 Payment — orderId 기반 복구")
    class OrphanRecoveryByOrderId {

        @Test
        @DisplayName("PG에 결제가 있으면 transactionKey를 매핑하고 콜백을 처리한다")
        void pollOrphan_WithPgPaymentExists_ShouldRecoverWithTransactionKey() {
            PaymentModel orphan = createOrphanPayment(100L);
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(orphan));
            given(paymentGateway.getPaymentsByOrderId(100L))
                    .willReturn(List.of(
                            new GatewayPaymentResult("TXN-RECOVERED", true, "SUCCESS", null)
                    ));

            scheduler.pollPendingPayments();

            verify(paymentService).assignTransactionKey(any(), eq("TXN-RECOVERED"));
            verify(paymentFacade).handleCallback("TXN-RECOVERED", "SUCCESS", null);
        }

        @Test
        @DisplayName("PG에 결제가 없으면 안전하게 FAILED 처리한다")
        void pollOrphan_WithNoPgPayment_ShouldMarkFailed() {
            PaymentModel orphan = createOrphanPayment(200L);
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(orphan));
            given(paymentGateway.getPaymentsByOrderId(200L))
                    .willReturn(List.of());

            scheduler.pollPendingPayments();

            verify(paymentService).completePayment(any(), eq(PaymentStatus.FAILED), anyString());
            verify(paymentFacade, never()).handleCallback(any(), any(), any());
        }

        @Test
        @DisplayName("PG에 PENDING만 있으면 다음 폴링에서 재시도한다")
        void pollOrphan_WithPgPending_ShouldSkipForNextPoll() {
            PaymentModel orphan = createOrphanPayment(300L);
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(orphan));
            given(paymentGateway.getPaymentsByOrderId(300L))
                    .willReturn(List.of(
                            new GatewayPaymentResult("TXN-PENDING", false, "PENDING", null)
                    ));

            scheduler.pollPendingPayments();

            verify(paymentService, never()).assignTransactionKey(any(), any());
            verify(paymentService, never()).completePayment(any(), any(), any());
            verify(paymentFacade, never()).handleCallback(any(), any(), any());
        }

        @Test
        @DisplayName("PG에 여러 건 중 완료된 건을 찾아 복구한다")
        void pollOrphan_WithMultiplePgResults_ShouldRecoverCompleted() {
            PaymentModel orphan = createOrphanPayment(400L);
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(orphan));
            given(paymentGateway.getPaymentsByOrderId(400L))
                    .willReturn(List.of(
                            new GatewayPaymentResult("TXN-FAILED", false, "FAILED", "잔액 부족"),
                            new GatewayPaymentResult("TXN-SUCCESS", true, "SUCCESS", null)
                    ));

            scheduler.pollPendingPayments();

            // 첫 번째 완료 건(FAILED)으로 복구
            verify(paymentService).assignTransactionKey(any(), eq("TXN-FAILED"));
            verify(paymentFacade).handleCallback("TXN-FAILED", "FAILED", "잔액 부족");
        }

        @Test
        @DisplayName("PG orderId 조회 자체가 실패해도 다른 건은 정상 처리한다")
        void pollOrphan_WithPgQueryFailure_ShouldContinueOthers() {
            PaymentModel orphan = createOrphanPayment(500L);
            PaymentModel normal = createPaymentWithTransactionKey("TXN-NORMAL");
            given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                    .willReturn(List.of(orphan, normal));
            // orderId 조회가 빈 리스트 반환 (PgHttpClient에서 예외 catch → List.of())
            given(paymentGateway.getPaymentsByOrderId(500L)).willReturn(List.of());
            given(paymentGateway.getPaymentStatus("TXN-NORMAL"))
                    .willReturn(new GatewayPaymentResult("TXN-NORMAL", true, "SUCCESS", null));

            scheduler.pollPendingPayments();

            // 고아는 FAILED 처리, 정상 건은 콜백 복구
            verify(paymentService).completePayment(any(), eq(PaymentStatus.FAILED), anyString());
            verify(paymentFacade).handleCallback("TXN-NORMAL", "SUCCESS", null);
        }
    }

    @Test
    @DisplayName("REQUESTED 결제가 없으면 아무 작업도 하지 않는다")
    void pollPendingPayments_WithNoRequested_ShouldDoNothing() {
        given(paymentService.findRequestedBefore(any(LocalDateTime.class)))
                .willReturn(List.of());

        scheduler.pollPendingPayments();

        verify(paymentGateway, never()).getPaymentStatus(any());
        verify(paymentGateway, never()).getPaymentsByOrderId(any());
        verify(paymentFacade, never()).handleCallback(any(), any(), any());
    }

    private PaymentModel createPaymentWithTransactionKey(String transactionKey) {
        PaymentModel payment = PaymentModel.create(1L, 1L, CardType.SAMSUNG,
                "1234-5678-9012-3456", BigDecimal.valueOf(10000));
        payment.assignTransactionKey(transactionKey);
        return payment;
    }

    private PaymentModel createOrphanPayment(Long orderId) {
        return PaymentModel.create(orderId, 1L, CardType.SAMSUNG,
                "1234-5678-9012-3456", BigDecimal.valueOf(10000));
    }
}
