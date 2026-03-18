package com.loopers.application.payment;

import com.loopers.application.order.OrderApp;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentFacade 단위 테스트")
class PaymentFacadeTest {

    @InjectMocks
    private PaymentFacade paymentFacade;

    @Mock
    private PaymentApp paymentApp;

    @Mock
    private OrderApp orderApp;

    private static final Long ORDER_ID = 1000001L;
    private static final Long MEMBER_ID = 1L;
    private static final Long PAYMENT_ID = 1L;
    private static final String PG_TRANSACTION_ID = "txKey-001";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    private PaymentInfo pendingPaymentInfo() {
        return new PaymentInfo(PAYMENT_ID, ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT, PaymentStatus.PENDING, null);
    }

    private PaymentInfo requestedPaymentInfo() {
        return new PaymentInfo(PAYMENT_ID, ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT, PaymentStatus.REQUESTED, PG_TRANSACTION_ID);
    }

    private PaymentInfo completedPaymentInfo() {
        return new PaymentInfo(PAYMENT_ID, ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT, PaymentStatus.COMPLETED, PG_TRANSACTION_ID);
    }

    private PaymentInfo failedPaymentInfo() {
        return new PaymentInfo(PAYMENT_ID, ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT, PaymentStatus.FAILED, PG_TRANSACTION_ID);
    }

    @Nested
    @DisplayName("결제 요청 (requestPayment)")
    class RequestPayment {

        @Test
        @DisplayName("PENDING 생성 후 requestToGateway 순서대로 호출된다")
        void requestPayment_callsCreateThenRequestInOrder() {
            PaymentCommand command = new PaymentCommand(ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT);
            given(paymentApp.createPendingPayment(command)).willReturn(pendingPaymentInfo());
            given(paymentApp.requestToGateway(PAYMENT_ID, CALLBACK_URL)).willReturn(requestedPaymentInfo());

            PaymentInfo result = paymentFacade.requestPayment(command, CALLBACK_URL);

            assertThat(result.status()).isEqualTo(PaymentStatus.REQUESTED);
            var inOrder = inOrder(paymentApp);
            inOrder.verify(paymentApp).createPendingPayment(command);
            inOrder.verify(paymentApp).requestToGateway(PAYMENT_ID, CALLBACK_URL);
        }
    }

    @Nested
    @DisplayName("PG 콜백 처리 (handleCallback)")
    class HandleCallback {

        @Test
        @DisplayName("COMPLETED 콜백 수신 시 Payment 처리 후 Order PAID 업데이트된다")
        void handleCallback_completed_updatesOrder() {
            given(paymentApp.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT)).willReturn(completedPaymentInfo());

            paymentFacade.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT);

            verify(orderApp).markOrderPaid(ORDER_ID);
        }

        @Test
        @DisplayName("COMPLETED 콜백 수신 시 Order가 이미 CANCELED이면 예외가 전파되지 않는다")
        void handleCallback_completed_orderAlreadyCanceled_doesNotPropagate() {
            given(paymentApp.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT)).willReturn(completedPaymentInfo());
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "주문 상태를 CANCELED에서 PAID로 변경할 수 없습니다."))
                    .when(orderApp).markOrderPaid(ORDER_ID);

            paymentFacade.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT);

            verify(orderApp).markOrderPaid(ORDER_ID);
        }

        @Test
        @DisplayName("FAILED 콜백 수신 시 Order 업데이트가 호출되지 않는다")
        void handleCallback_failed_doesNotUpdateOrder() {
            given(paymentApp.handleCallback(PG_TRANSACTION_ID, PgStatus.FAILED, AMOUNT)).willReturn(failedPaymentInfo());

            paymentFacade.handleCallback(PG_TRANSACTION_ID, PgStatus.FAILED, AMOUNT);

            verify(orderApp, never()).markOrderPaid(any());
        }

        @Test
        @DisplayName("중복 콜백 수신 시 PaymentApp에서 발생한 예외가 전파된다")
        void handleCallback_duplicate_propagatesException() {
            given(paymentApp.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT))
                    .willThrow(new CoreException(ErrorType.BAD_REQUEST, "결제 상태 전이 불가: COMPLETED → COMPLETED"));

            assertThatThrownBy(() -> paymentFacade.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("COMPLETED 콜백 수신 시 OrderApp에서 낙관락 예외가 발생하면 전파되어 PG가 재전송할 수 있다")
        void handleCallback_completed_optimisticLockException_propagates() {
            given(paymentApp.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT)).willReturn(completedPaymentInfo());
            doThrow(new RuntimeException("OptimisticLockingFailureException"))
                    .when(orderApp).markOrderPaid(ORDER_ID);

            assertThatThrownBy(() -> paymentFacade.handleCallback(PG_TRANSACTION_ID, PgStatus.SUCCESS, AMOUNT))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("결제 상태 동기화 (syncPayment)")
    class SyncPayment {

        @Test
        @DisplayName("PG에서 COMPLETED 확인 시 Order PAID 업데이트된다")
        void syncPayment_completed_updatesOrder() {
            given(paymentApp.syncFromGateway(PAYMENT_ID, MEMBER_ID)).willReturn(completedPaymentInfo());

            PaymentInfo result = paymentFacade.syncPayment(PAYMENT_ID, MEMBER_ID);

            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(orderApp).markOrderPaid(ORDER_ID);
        }

        @Test
        @DisplayName("PG에서 PENDING 확인 시 Order 업데이트가 호출되지 않는다")
        void syncPayment_pending_doesNotUpdateOrder() {
            given(paymentApp.syncFromGateway(PAYMENT_ID, MEMBER_ID)).willReturn(requestedPaymentInfo());

            paymentFacade.syncPayment(PAYMENT_ID, MEMBER_ID);

            verify(orderApp, never()).markOrderPaid(any());
        }
    }
}
