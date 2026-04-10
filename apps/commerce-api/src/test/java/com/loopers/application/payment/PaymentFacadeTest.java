package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.domain.order.OrderErrorCode;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgRequestStatus;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @Mock PaymentService paymentService;
    @Mock PaymentGateway paymentGateway;
    @Mock OrderService orderService;
    @InjectMocks PaymentFacade paymentFacade;

    @DisplayName("결제를 요청할 때, ")
    @Nested
    class RequestPayment {

        @DisplayName("Payment를 생성하고 PG를 호출한다.")
        @Test
        void requestPayment_createsPendingAndCallsPg() {
            // arrange
            PaymentModel payment = mockPayment(1L, 1L, 50000, PaymentStatus.PENDING);
            when(paymentService.createPending(eq(1L), eq(50000), eq(CardType.SAMSUNG), anyString()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(PgPaymentRequest.class)))
                    .thenReturn(new PgPaymentResult(
                            true, "20260316:TR:abc", PgRequestStatus.ACCEPTED));

            // act
            PaymentResult result = paymentFacade.requestPayment(
                    100L,
                    new PaymentCriteria.Create(1L, 50000, CardType.SAMSUNG, "1234-5678-9814-1451"));

            // assert
            assertAll(
                    () -> assertThat(result.paymentId()).isEqualTo(1L),
                    () -> assertThat(result.status()).isEqualTo("PENDING"));
            verify(paymentService).createPending(eq(1L), eq(50000), eq(CardType.SAMSUNG), anyString());
            verify(paymentGateway).requestPayment(any(PgPaymentRequest.class));
            verify(paymentService).updateRequested(1L, "20260316:TR:abc");
        }

        @DisplayName("PG 서버가 불안정하면 Payment를 실패 처리하고 예외를 던진다.")
        @Test
        void requestPayment_whenPgUnavailable_failsPayment() {
            // arrange
            PaymentModel payment = mockPayment(1L, 1L, 50000, PaymentStatus.PENDING);
            when(paymentService.createPending(eq(1L), eq(50000), eq(CardType.SAMSUNG), anyString()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(PgPaymentRequest.class)))
                    .thenReturn(new PgPaymentResult(false, null, PgRequestStatus.SERVER_ERROR,
                            "현재 서버가 불안정합니다."));

            // act & assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(
                    100L,
                    new PaymentCriteria.Create(1L, 50000, CardType.SAMSUNG, "1234-5678-9814-1451")))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제 서비스가 일시적으로 지연되고 있습니다");
            verify(paymentService).failById(1L);
        }

        @DisplayName("PG 요청 파라미터가 잘못되면 Payment를 실패 처리하고 예외를 던진다.")
        @Test
        void requestPayment_whenPgBadRequest_failsPayment() {
            // arrange
            PaymentModel payment = mockPayment(1L, 1L, 50000, PaymentStatus.PENDING);
            when(paymentService.createPending(eq(1L), eq(50000), eq(CardType.SAMSUNG), anyString()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(PgPaymentRequest.class)))
                    .thenReturn(new PgPaymentResult(false, null, PgRequestStatus.VALIDATION_ERROR,
                            "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다."));

            // act & assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(
                    100L,
                    new PaymentCriteria.Create(1L, 50000, CardType.SAMSUNG, "invalid")))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다");
            verify(paymentService).failById(1L);
        }
    }

    @DisplayName("콜백을 처리할 때, ")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백이면 Payment 완료 후 Order를 완료한다.")
        @Test
        void handleCallback_success_completesPaymentAndOrder() {
            // arrange
            PaymentModel payment = mockPayment(1L, 1L, 50000, PaymentStatus.COMPLETED);
            when(paymentService.updateCompleted("PK_001")).thenReturn(payment);

            // act
            paymentFacade.handleCallback("PK_001", "SUCCESS", "정상 승인되었습니다.");

            // assert
            verify(paymentService).updateCompleted("PK_001");
            verify(orderService).completeOrder(1L);
        }

        @DisplayName("FAILED 콜백이면 Payment를 실패 처리한다.")
        @Test
        void handleCallback_failed_failsPayment() {
            // arrange
            PaymentModel payment = mockPayment(1L, 1L, 50000, PaymentStatus.FAILED);
            when(paymentService.updateFailed(
                    eq("PK_002"), eq("LIMIT_EXCEEDED"), anyString()))
                    .thenReturn(payment);

            // act
            paymentFacade.handleCallback("PK_002", "FAILED",
                    "한도초과입니다. 다른 카드를 선택해주세요.");

            // assert
            verify(paymentService).updateFailed(
                    eq("PK_002"), eq("LIMIT_EXCEEDED"), anyString());
            verify(orderService, never()).completeOrder(any());
        }

        @DisplayName("SUCCESS 콜백이지만 주문이 이미 취소됐으면 로그를 남긴다.")
        @Test
        void handleCallback_success_butOrderCancelled_logsWarning() {
            // arrange
            PaymentModel payment = mockPayment(1L, 1L, 50000, PaymentStatus.COMPLETED);
            when(paymentService.updateCompleted("PK_LATE")).thenReturn(payment);
            org.mockito.Mockito.doThrow(new CoreException(OrderErrorCode.INVALID_STATUS_TRANSITION))
                    .when(orderService).completeOrder(1L);

            OrderModel cancelledOrder = org.mockito.Mockito.mock(OrderModel.class);
            lenient().when(cancelledOrder.isCancelled()).thenReturn(true);
            when(orderService.getById(1L)).thenReturn(cancelledOrder);

            // act
            paymentFacade.handleCallback("PK_LATE", "SUCCESS", "정상 승인되었습니다.");

            // assert
            verify(orderService).getById(1L);
        }
    }

    private PaymentModel mockPayment(Long paymentId, Long orderId,
                                     int amount, PaymentStatus status) {
        PaymentModel payment = org.mockito.Mockito.mock(PaymentModel.class);
        lenient().when(payment.getId()).thenReturn(paymentId);
        lenient().when(payment.getOrderId()).thenReturn(orderId);
        lenient().when(payment.getAmount()).thenReturn(amount);
        lenient().when(payment.getStatus()).thenReturn(status);
        lenient().when(payment.getCardType()).thenReturn(CardType.SAMSUNG);
        lenient().when(payment.getMaskedCardNo()).thenReturn("****-****-****-1451");
        lenient().when(payment.getCreatedAt()).thenReturn(ZonedDateTime.now());
        lenient().when(payment.isCompleted()).thenReturn(status == PaymentStatus.COMPLETED);
        lenient().when(payment.isFailed()).thenReturn(status == PaymentStatus.FAILED);
        return payment;
    }
}
