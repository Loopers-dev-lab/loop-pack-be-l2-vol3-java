package com.loopers.application.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.OrderTransactionResult;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentFixture;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.TransactionDetailResult;
import com.loopers.domain.payment.TransactionResult;

@ExtendWith(MockitoExtension.class)
class PaymentRecoverySchedulerTest {

    @InjectMocks
    private PaymentRecoveryScheduler scheduler;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;

    @Mock
    private PaymentProcessor paymentProcessor;

    @Mock
    private OrderService orderService;

    @DisplayName("PENDING 결제를 동기화할 때,")
    @Nested
    class SyncPendingPayments {

        @DisplayName("PG 조회 결과가 SUCCESS이면, 콜백 처리를 위임한다.")
        @Test
        void delegatesToCallbackHandler_whenPgReturnsSuccess() {
            // arrange
            Payment pendingPayment = PaymentFixture.createPendingPayment();
            given(paymentService.getPendingPaymentsBefore(any())).willReturn(List.of(pendingPayment));
            given(paymentGateway.getTransaction(pendingPayment.getUserId(), pendingPayment.getTransactionKey()))
                    .willReturn(new TransactionDetailResult(
                            "txn-key-123", "order-1", "SHINHAN", "1234", 50000L, "SUCCESS", null
                    ));

            // act
            scheduler.syncPendingPayments();

            // assert
            then(handlePaymentCallbackUseCase).should().execute(
                    new PaymentCallbackCommand("txn-key-123", PaymentStatus.SUCCESS, null)
            );
        }

        @DisplayName("PG 조회 결과가 FAILED이면, 콜백 처리를 위임한다.")
        @Test
        void delegatesToCallbackHandler_whenPgReturnsFailed() {
            // arrange
            Payment pendingPayment = PaymentFixture.createPendingPayment();
            given(paymentService.getPendingPaymentsBefore(any())).willReturn(List.of(pendingPayment));
            given(paymentGateway.getTransaction(pendingPayment.getUserId(), pendingPayment.getTransactionKey()))
                    .willReturn(new TransactionDetailResult(
                            "txn-key-123", "order-1", "SHINHAN", "1234", 50000L, "FAILED", "잔액 부족"
                    ));

            // act
            scheduler.syncPendingPayments();

            // assert
            then(handlePaymentCallbackUseCase).should().execute(
                    new PaymentCallbackCommand("txn-key-123", PaymentStatus.FAILED, "잔액 부족")
            );
        }

        @DisplayName("PG 조회 결과가 PENDING이면, 콜백 처리를 위임하지 않는다.")
        @Test
        void skips_whenPgReturnsPending() {
            // arrange
            Payment pendingPayment = PaymentFixture.createPendingPayment();
            given(paymentService.getPendingPaymentsBefore(any())).willReturn(List.of(pendingPayment));
            given(paymentGateway.getTransaction(pendingPayment.getUserId(), pendingPayment.getTransactionKey()))
                    .willReturn(new TransactionDetailResult(
                            "txn-key-123", "order-1", "SHINHAN", "1234", 50000L, "PENDING", null
                    ));

            // act
            scheduler.syncPendingPayments();

            // assert
            then(handlePaymentCallbackUseCase).should(never()).execute(any());
        }

        @DisplayName("PG에서 알 수 없는 상태를 반환하면, 콜백 처리를 위임하지 않는다.")
        @Test
        void skips_whenPgReturnsUnknownStatus() {
            // arrange
            Payment pendingPayment = PaymentFixture.createPendingPayment();
            given(paymentService.getPendingPaymentsBefore(any())).willReturn(List.of(pendingPayment));
            given(paymentGateway.getTransaction(pendingPayment.getUserId(), pendingPayment.getTransactionKey()))
                    .willReturn(new TransactionDetailResult(
                            "txn-key-123", "order-1", "SHINHAN", "1234", 50000L, "UNKNOWN_STATUS", null
                    ));

            // act
            scheduler.syncPendingPayments();

            // assert
            then(handlePaymentCallbackUseCase).should(never()).execute(any());
        }

        @DisplayName("PG 조회 중 예외가 발생하면, 해당 건을 스킵하고 나머지를 처리한다.")
        @Test
        void skipsFailedAndProcessesRemaining_whenPgThrowsException() {
            // arrange
            Payment failingPayment = PaymentFixture.createPendingPayment();
            Payment successPayment = PaymentFixture.createPendingPaymentWithTransactionKey("txn-key-456");
            given(paymentService.getPendingPaymentsBefore(any())).willReturn(List.of(failingPayment, successPayment));
            given(paymentGateway.getTransaction(failingPayment.getUserId(), failingPayment.getTransactionKey()))
                    .willThrow(new RuntimeException("PG 연결 실패"));
            given(paymentGateway.getTransaction(successPayment.getUserId(), "txn-key-456"))
                    .willReturn(new TransactionDetailResult(
                            "txn-key-456", "order-2", "SHINHAN", "1234", 50000L, "SUCCESS", null
                    ));

            // act
            scheduler.syncPendingPayments();

            // assert
            then(handlePaymentCallbackUseCase).should().execute(
                    new PaymentCallbackCommand("txn-key-456", PaymentStatus.SUCCESS, null)
            );
        }

        @DisplayName("PENDING 결제가 없으면, 아무 처리도 하지 않는다.")
        @Test
        void doesNothing_whenNoPendingPayments() {
            // arrange
            given(paymentService.getPendingPaymentsBefore(any())).willReturn(List.of());

            // act
            scheduler.syncPendingPayments();

            // assert
            then(paymentGateway).should(never()).getTransaction(any(), any());
            then(handlePaymentCallbackUseCase).should(never()).execute(any());
        }
    }

    @DisplayName("READY 결제를 복구할 때,")
    @Nested
    class RecoverReadyPayments {

        @DisplayName("PG에 SUCCESS 거래가 1건이면, PaymentProcessor에 복구를 위임한다.")
        @Test
        void delegatesToPaymentProcessor_whenSingleSuccessTransaction() {
            // arrange
            Payment readyPayment = PaymentFixture.createReadyPayment();
            Order order = mock(Order.class);
            given(order.getOrderKey()).willReturn("test-order-key");

            given(paymentService.getReadyPaymentsBefore(any())).willReturn(List.of(readyPayment));
            given(orderService.getById(readyPayment.getOrderId())).willReturn(order);
            given(paymentGateway.getTransactionsByOrder(readyPayment.getUserId(), "test-order-key"))
                    .willReturn(new OrderTransactionResult(
                            "test-order-key",
                            List.of(new TransactionResult("txn-recovered", "SUCCESS", null))
                    ));

            // act
            scheduler.recoverReadyPayments();

            // assert
            then(paymentProcessor).should().recoverWithTransaction(
                    readyPayment.getId(), "txn-recovered", PaymentStatus.SUCCESS, null
            );
        }

        @DisplayName("PG에 거래가 없으면, PaymentProcessor에 실패 복구를 위임한다.")
        @Test
        void delegatesToPaymentProcessor_whenNoTransactionInPg() {
            // arrange
            Payment readyPayment = PaymentFixture.createReadyPayment();
            Order order = mock(Order.class);
            given(order.getOrderKey()).willReturn("test-order-key");

            given(paymentService.getReadyPaymentsBefore(any())).willReturn(List.of(readyPayment));
            given(orderService.getById(readyPayment.getOrderId())).willReturn(order);
            given(paymentGateway.getTransactionsByOrder(readyPayment.getUserId(), "test-order-key"))
                    .willReturn(new OrderTransactionResult("test-order-key", List.of()));

            // act
            scheduler.recoverReadyPayments();

            // assert
            then(paymentProcessor).should().recoverWithoutTransaction(
                    readyPayment.getId(), "PG 결제 요청 타임아웃으로 거래 없음"
            );
        }

        @DisplayName("PG에 거래가 모두 FAILED이면, PaymentProcessor에 실패 복구를 위임한다.")
        @Test
        void delegatesToPaymentProcessor_whenAllTransactionsFailed() {
            // arrange
            Payment readyPayment = PaymentFixture.createReadyPayment();
            Order order = mock(Order.class);
            given(order.getOrderKey()).willReturn("test-order-key");

            given(paymentService.getReadyPaymentsBefore(any())).willReturn(List.of(readyPayment));
            given(orderService.getById(readyPayment.getOrderId())).willReturn(order);
            given(paymentGateway.getTransactionsByOrder(readyPayment.getUserId(), "test-order-key"))
                    .willReturn(new OrderTransactionResult("test-order-key", List.of(
                            new TransactionResult("txn-1", "FAILED", "잔액 부족"),
                            new TransactionResult("txn-2", "FAILED", "카드 오류")
                    )));

            // act
            scheduler.recoverReadyPayments();

            // assert
            then(paymentProcessor).should().recoverWithoutTransaction(
                    readyPayment.getId(), "PG 결제 요청 타임아웃으로 거래 없음"
            );
        }

        @DisplayName("PG에 SUCCESS 거래가 2건 이상이면, 복구하지 않고 스킵한다.")
        @Test
        void skipsRecovery_whenMultipleSuccessTransactions() {
            // arrange
            Payment readyPayment = PaymentFixture.createReadyPayment();
            Order order = mock(Order.class);
            given(order.getOrderKey()).willReturn("test-order-key");

            given(paymentService.getReadyPaymentsBefore(any())).willReturn(List.of(readyPayment));
            given(orderService.getById(readyPayment.getOrderId())).willReturn(order);
            given(paymentGateway.getTransactionsByOrder(readyPayment.getUserId(), "test-order-key"))
                    .willReturn(new OrderTransactionResult("test-order-key", List.of(
                            new TransactionResult("txn-1", "SUCCESS", null),
                            new TransactionResult("txn-2", "SUCCESS", null)
                    )));

            // act
            scheduler.recoverReadyPayments();

            // assert
            then(paymentProcessor).should(never()).recoverWithTransaction(any(), any(), any(), any());
            then(paymentProcessor).should(never()).recoverWithoutTransaction(any(), any());
        }

        @DisplayName("PG 조회 중 예외가 발생하면, 해당 건을 스킵하고 나머지를 처리한다.")
        @Test
        void skipsFailedAndProcessesRemaining_whenExceptionOccurs() {
            // arrange
            Payment failingPayment = PaymentFixture.createReadyPayment();
            Payment successPayment = PaymentFixture.createReadyPayment();
            Order order = mock(Order.class);
            given(order.getOrderKey()).willReturn("test-order-key");

            given(paymentService.getReadyPaymentsBefore(any())).willReturn(List.of(failingPayment, successPayment));
            given(orderService.getById(failingPayment.getOrderId()))
                    .willThrow(new RuntimeException("주문 조회 실패"))
                    .willReturn(order);
            given(paymentGateway.getTransactionsByOrder(successPayment.getUserId(), "test-order-key"))
                    .willReturn(new OrderTransactionResult("test-order-key", List.of()));

            // act
            scheduler.recoverReadyPayments();

            // assert
            then(paymentProcessor).should().recoverWithoutTransaction(
                    successPayment.getId(), "PG 결제 요청 타임아웃으로 거래 없음"
            );
        }

        @DisplayName("READY 결제가 없으면, 아무 처리도 하지 않는다.")
        @Test
        void doesNothing_whenNoReadyPayments() {
            // arrange
            given(paymentService.getReadyPaymentsBefore(any())).willReturn(List.of());

            // act
            scheduler.recoverReadyPayments();

            // assert
            then(paymentGateway).should(never()).getTransactionsByOrder(any(), any());
            then(paymentProcessor).should(never()).recoverWithTransaction(any(), any(), any(), any());
            then(paymentProcessor).should(never()).recoverWithoutTransaction(any(), any());
        }
    }
}
