package com.loopers.application.payment;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentFixture;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class PaymentRecovererTest {

    @InjectMocks
    private PaymentRecoverer paymentRecoverer;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @DisplayName("PG 거래가 존재하는 READY 결제를 복구할 때,")
    @Nested
    class RecoverWithTransaction {

        @DisplayName("SUCCESS이면, 결제를 확정하고 주문을 결제 완료 상태로 변경한다.")
        @Test
        void confirmsAndPaysOrder_whenStatusIsSuccess() {
            // arrange
            Payment payment = PaymentFixture.createReadyPayment();
            given(paymentService.confirmPayment(payment.getId(), "txn-recovered"))
                    .willReturn(payment);

            // act
            paymentRecoverer.recoverWithTransaction(
                    payment.getId(), "txn-recovered", PaymentStatus.SUCCESS, null
            );

            // assert
            then(paymentService).should().confirmPayment(payment.getId(), "txn-recovered");
            then(orderService).should().pay(payment.getOrderId());
        }

        @DisplayName("FAILED이면, 결제를 확정하고 주문을 실패 처리한다.")
        @Test
        void confirmsAndFailsOrder_whenStatusIsFailed() {
            // arrange
            Payment payment = PaymentFixture.createReadyPayment();
            given(paymentService.confirmPayment(payment.getId(), "txn-failed"))
                    .willReturn(payment);

            // act
            paymentRecoverer.recoverWithTransaction(
                    payment.getId(), "txn-failed", PaymentStatus.FAILED, "잔액 부족"
            );

            // assert
            then(paymentService).should().confirmPayment(payment.getId(), "txn-failed");
            then(orderService).should().fail(payment.getOrderId());
        }
    }

    @DisplayName("PG 거래가 없는 READY 결제를 복구할 때,")
    @Nested
    class RecoverWithoutTransaction {

        @DisplayName("결제를 실패 처리하고 주문을 실패 처리한다.")
        @Test
        void failsPaymentAndOrder() {
            // arrange
            Payment payment = PaymentFixture.createReadyPayment();
            given(paymentService.fail(payment.getId(), "PG 결제 요청 타임아웃으로 거래 없음"))
                    .willReturn(payment);

            // act
            paymentRecoverer.recoverWithoutTransaction(
                    payment.getId(), "PG 결제 요청 타임아웃으로 거래 없음"
            );

            // assert
            then(paymentService).should().fail(payment.getId(), "PG 결제 요청 타임아웃으로 거래 없음");
            then(orderService).should().fail(payment.getOrderId());
        }
    }
}
