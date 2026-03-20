package com.loopers.application.payment;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.payment.command.CompletePaymentCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.payment.CardType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentUseCaseTest {

    private static final String MEMBER_ID = "member-1";
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PaymentStartApplicationService paymentStartApplicationService;

    @Mock
    private PaymentCancelApplicationService paymentCancelApplicationService;

    @Mock
    private PaymentCompletionApplicationService paymentCompletionApplicationService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private OrderApplicationService orderApplicationService;

    @InjectMocks
    private PaymentUseCase paymentUseCase;

    @Test
    void reconcileUsesTransactionKeyPathWhenAlreadySet() {
        Payment payment = requestPaymentWithTransactionKey();
        Order order = mock(Order.class);
        when(order.isCancelled()).thenReturn(false);

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderApplicationService.getById(any(OrderAccessRequest.class))).thenReturn(order);

        Payment completed = payment.markSucceeded(payment.pgTransactionKey());
        when(paymentCompletionApplicationService.complete(new CompletePaymentCommand(MEMBER_ID, payment.pgTransactionKey())))
                .thenReturn(completed);

        Payment actual = paymentUseCase.reconcile(MEMBER_ID, ORDER_ID);

        assertThat(actual).isEqualTo(completed);
        verify(paymentGateway, never()).getPaymentsByOrderId(MEMBER_ID, ORDER_ID.toString());
    }

    @Test
    void reconcileFailsWhenOrderHasMultipleGatewayTransactions() {
        Payment payment = requestPaymentWithoutTransactionKey();
        Order order = mock(Order.class);

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderApplicationService.getById(any(OrderAccessRequest.class))).thenReturn(order);
        when(paymentGateway.getPaymentsByOrderId(MEMBER_ID, ORDER_ID.toString())).thenReturn(List.of(
                new PaymentGateway.PaymentGatewayTransaction("trx-1", ORDER_ID.toString(), PaymentStatus.SUCCEEDED, null),
                new PaymentGateway.PaymentGatewayTransaction("trx-2", ORDER_ID.toString(), PaymentStatus.CANCELLED, null)
        ));

        assertThatThrownBy(() -> paymentUseCase.reconcile(MEMBER_ID, ORDER_ID))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("여러 건");
    }

    @Test
    void reconcileRetriesCancelAfterCompletionWhenOrderCancelledAndPaymentStillRequested() {
        Payment payment = requestPaymentWithTransactionKey();
        Order order = mock(Order.class);
        when(order.isCancelled()).thenReturn(true);

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderApplicationService.getById(any(OrderAccessRequest.class))).thenReturn(order);

        when(paymentCancelApplicationService.cancel(any()))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "취소 대상 없음"))
                .thenReturn(payment.requestCancel());

        when(paymentCompletionApplicationService.complete(new CompletePaymentCommand(MEMBER_ID, payment.pgTransactionKey())))
                .thenReturn(payment);

        Payment actual = paymentUseCase.reconcile(MEMBER_ID, ORDER_ID);

        assertThat(actual.status()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verify(paymentCancelApplicationService, times(2)).cancel(any());
    }

    @Test
    void reconcileRetriesGatewayLookupWhenCancelledOrderHasRequestedPaymentWithoutTransactionKey() {
        Payment payment = requestPaymentWithoutTransactionKey();
        Order order = mock(Order.class);
        when(order.isCancelled()).thenReturn(true);

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderApplicationService.getById(any(OrderAccessRequest.class))).thenReturn(order);

        when(paymentGateway.getPaymentsByOrderId(MEMBER_ID, ORDER_ID.toString()))
                .thenReturn(List.of())
                .thenReturn(List.of(new PaymentGateway.PaymentGatewayTransaction(
                        "trx-recovered",
                        ORDER_ID.toString(),
                        PaymentStatus.REQUESTED,
                        null
                )));

        Payment recovered = new Payment(
                payment.id(),
                payment.memberId(),
                payment.orderId(),
                payment.cardType(),
                payment.cardNo(),
                payment.amount(),
                payment.status(),
                "trx-recovered",
                payment.reason(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.deletedAt()
        );
        when(paymentRepository.save(any(Payment.class))).thenReturn(recovered);
        when(paymentCancelApplicationService.cancel(any())).thenReturn(recovered.requestCancel());

        Payment actual = paymentUseCase.reconcile(MEMBER_ID, ORDER_ID);

        assertThat(actual.status()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verify(paymentGateway, times(2)).getPaymentsByOrderId(MEMBER_ID, ORDER_ID.toString());
    }

    @Test
    void reconcileMarksCancelReconcileRequiredWhenCancelledRequestedPaymentHasNoRecoverableTransaction() {
        Payment payment = requestPaymentWithoutTransactionKey();
        Order order = mock(Order.class);
        when(order.isCancelled()).thenReturn(true);

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderApplicationService.getById(any(OrderAccessRequest.class))).thenReturn(order);

        when(paymentGateway.getPaymentsByOrderId(MEMBER_ID, ORDER_ID.toString()))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment actual = paymentUseCase.reconcile(MEMBER_ID, ORDER_ID);

        assertThat(actual.status()).isEqualTo(PaymentStatus.CANCEL_RECONCILE_REQUIRED);
        assertThat(actual.reason()).contains("PG 주문 결제 조회 재처리 대기");
        verify(paymentGateway, times(3)).getPaymentsByOrderId(MEMBER_ID, ORDER_ID.toString());
    }

    @Test
    void reconcileMarksCancelReconcileRequiredWhenSecondCancelAttemptStillFailsAfterCompletion() {
        Payment payment = requestPaymentWithTransactionKey();
        Order order = mock(Order.class);
        when(order.isCancelled()).thenReturn(true);

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderApplicationService.getById(any(OrderAccessRequest.class))).thenReturn(order);

        Payment completed = payment.markSucceeded(payment.pgTransactionKey());
        when(paymentCompletionApplicationService.complete(new CompletePaymentCommand(MEMBER_ID, payment.pgTransactionKey())))
                .thenReturn(completed);

        when(paymentCancelApplicationService.cancel(any()))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "취소 대상 없음"))
                .thenThrow(new CoreException(ErrorType.INTERNAL_ERROR, "취소 재시도 실패"));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment actual = paymentUseCase.reconcile(MEMBER_ID, ORDER_ID);

        assertThat(actual.status()).isEqualTo(PaymentStatus.CANCEL_RECONCILE_REQUIRED);
        assertThat(actual.reason()).contains("결제 취소 재처리 대기");
        verify(paymentCancelApplicationService, times(2)).cancel(any());
    }

    private Payment requestPaymentWithTransactionKey() {
        return new Payment(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                MEMBER_ID,
                ORDER_ID,
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                15000,
                PaymentStatus.REQUESTED,
                "trx-1",
                null,
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                null
        );
    }

    private Payment requestPaymentWithoutTransactionKey() {
        return new Payment(
                UUID.fromString("22222222-2222-2222-2222-222222222223"),
                MEMBER_ID,
                ORDER_ID,
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                15000,
                PaymentStatus.REQUESTED,
                null,
                null,
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                null
        );
    }
}
