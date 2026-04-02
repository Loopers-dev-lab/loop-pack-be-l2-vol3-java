package com.loopers.application.payment;

import com.loopers.application.payment.command.CancelPaymentCommand;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayTransaction;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PaymentRecoveryRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCancelApplicationServiceTest {

    private static final String MEMBER_ID = "member-1";
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentCancelApplicationService paymentCancelApplicationService;

    @Test
    @DisplayName("이미 CANCELLED 상태면 PG 호출 없이 기존 상태를 반환한다")
    void cancelReturnsExistingWhenAlreadyCancelled() {
        Payment cancelled = cancelledPayment();
        when(paymentRepository.findByMemberIdAndOrderIdForUpdate(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(cancelled));

        Payment actual = paymentCancelApplicationService.cancel(cancelCommand());

        assertThat(actual).isSameAs(cancelled);
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("CANCEL_REQUESTED 재요청 시 PG 취소를 다시 호출하지 않고 기존 상태를 반환한다")
    void cancelDuplicateRequestCallsPgOnce() {
        Payment succeeded = succeededPayment();
        Payment cancelRequested = cancelRequestedPayment();

        when(paymentRepository.findByMemberIdAndOrderIdForUpdate(MEMBER_ID, ORDER_ID))
                .thenReturn(Optional.of(succeeded))
                .thenReturn(Optional.of(cancelRequested));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.cancelPayment(any())).thenReturn(cancelRequestedTransaction());

        Payment first = paymentCancelApplicationService.cancel(cancelCommand());
        Payment second = paymentCancelApplicationService.cancel(cancelCommand());

        assertThat(first.status()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(second.status()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verify(paymentGateway, times(1)).cancelPayment(any());
        assertThat(second.pgTransactionKey()).isEqualTo("trx-1");
    }

    @Test
    @DisplayName("PG 취소 상태 불명확 시 RECONCILE_REQUIRED 상태로 전이한다")
    void cancelMarksReconcileRequiredWhenRecoveryRequired() {
        Payment succeeded = succeededPayment();

        when(paymentRepository.findByMemberIdAndOrderIdForUpdate(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(succeeded));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.cancelPayment(any())).thenThrow(new PaymentRecoveryRequiredException("cancel recovery"));

        Payment actual = paymentCancelApplicationService.cancel(cancelCommand());

        assertThat(actual.status()).isEqualTo(PaymentStatus.CANCEL_RECONCILE_REQUIRED);
        assertThat(actual.reason()).isEqualTo("cancel recovery");
    }

    private CancelPaymentCommand cancelCommand() {
        return new CancelPaymentCommand(MEMBER_ID, ORDER_ID);
    }

    private Payment succeededPayment() {
        return new Payment(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                MEMBER_ID,
                ORDER_ID,
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                15000,
                PaymentStatus.SUCCEEDED,
                "trx-1",
                null,
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                null
        );
    }

    private Payment cancelledPayment() {
        return new Payment(
                UUID.fromString("22222222-2222-2222-2222-222222222223"),
                MEMBER_ID,
                ORDER_ID,
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                15000,
                PaymentStatus.CANCELLED,
                "trx-2",
                null,
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                null
        );
    }

    private Payment cancelRequestedPayment() {
        return new Payment(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                MEMBER_ID,
                ORDER_ID,
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                15000,
                PaymentStatus.CANCEL_REQUESTED,
                "trx-1",
                null,
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                null
        );
    }

    private PaymentGatewayTransaction cancelRequestedTransaction() {
        return new PaymentGatewayTransaction("trx-1", ORDER_ID.toString(), PaymentStatus.CANCEL_REQUESTED, null);
    }
}
