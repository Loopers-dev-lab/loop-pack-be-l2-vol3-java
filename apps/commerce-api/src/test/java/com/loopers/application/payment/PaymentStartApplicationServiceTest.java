package com.loopers.application.payment;

import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PaymentRecoveryRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStartApplicationServiceTest {

    private static final String MEMBER_ID = "member-1";
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CALLBACK_URL = "https://example.com/callback";

    @Mock
    private PaymentStartPreparationApplicationService paymentStartPreparationApplicationService;

    @Mock
    private PaymentStartCompletionApplicationService paymentStartCompletionApplicationService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentStartApplicationService paymentStartApplicationService;

    @Test
    @DisplayName("중복 요청일 때 기존 REQUESTED 결제를 바로 반환한다")
    void startReturnsExistingPaymentWhenAlreadyRequested() {
        Payment existing = existingPayment();
        when(paymentStartPreparationApplicationService.prepare(startCommand()))
                .thenReturn(new PaymentStartPreparationResult(existing, false));

        Payment actual = paymentStartApplicationService.start(startCommand());

        assertThat(actual).isSameAs(existing);
        verify(paymentGateway, never()).requestPayment(any());
    }

    @Test
    @DisplayName("준비 단계가 게이트웨이 호출 불필요로 판단하면 기존 결제를 바로 반환한다")
    void startReturnsExistingPaymentWhenPreparationMarksGatewayAsUnnecessary() {
        Payment existing = existingPayment();

        when(paymentStartPreparationApplicationService.prepare(startCommand()))
                .thenReturn(new PaymentStartPreparationResult(existing, false));

        Payment actual = paymentStartApplicationService.start(startCommand());

        assertThat(actual).isSameAs(existing);
        verify(paymentGateway, never()).requestPayment(any());
    }

    @Test
    @DisplayName("PG 응답 불명확하면 이미 생성된 결제 상태를 유지한다")
    void startKeepsRequestedPaymentWhenRecoveryRequired() {
        Payment saved = existingPayment();

        when(paymentStartPreparationApplicationService.prepare(startCommand()))
                .thenReturn(new PaymentStartPreparationResult(saved, true));
        when(paymentGateway.requestPayment(any())).thenThrow(new PaymentRecoveryRequiredException("request recovery"));

        Payment actual = paymentStartApplicationService.start(startCommand());

        assertThat(actual).isSameAs(saved);
        assertThat(actual.status()).isEqualTo(PaymentStatus.REQUESTED);
        verify(paymentStartCompletionApplicationService, never()).complete(any(Payment.class), any(PaymentGateway.PaymentGatewayTransaction.class));
    }

    @Test
    @DisplayName("PG 호출 성공 후에는 별도 완료 서비스에서 상태를 저장한다")
    void startCompletesPaymentAfterGatewayRequestOutsidePreparationTransaction() {
        Payment requested = existingPayment();
        Payment completed = requested.markSucceeded("tx-123");
        PaymentGateway.PaymentGatewayTransaction gatewayTransaction = new PaymentGateway.PaymentGatewayTransaction(
                "tx-123",
                requested.orderId().toString(),
                PaymentStatus.SUCCEEDED,
                null
        );

        when(paymentStartPreparationApplicationService.prepare(startCommand()))
                .thenReturn(new PaymentStartPreparationResult(requested, true));
        when(paymentGateway.requestPayment(any())).thenReturn(gatewayTransaction);
        when(paymentStartCompletionApplicationService.complete(requested, gatewayTransaction)).thenReturn(completed);

        Payment actual = paymentStartApplicationService.start(startCommand());

        assertThat(actual).isEqualTo(completed);
        verify(paymentGateway).requestPayment(any());
        verify(paymentStartCompletionApplicationService).complete(requested, gatewayTransaction);
    }

    private Payment existingPayment() {
        return new Payment(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
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

    private StartPaymentCommand startCommand() {
        return new StartPaymentCommand(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-1234-5678", 15000, CALLBACK_URL);
    }
}
