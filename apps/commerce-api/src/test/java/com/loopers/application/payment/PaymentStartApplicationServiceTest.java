package com.loopers.application.payment;

import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PaymentRecoveryRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;
import java.util.Optional;
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
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentStartApplicationService paymentStartApplicationService;

    @Test
    @DisplayName("중복 요청일 때 기존 REQUESTED 결제를 바로 반환한다")
    void startReturnsExistingPaymentWhenAlreadyRequested() {
        Payment existing = existingPayment();
        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.of(existing));

        Payment actual = paymentStartApplicationService.start(startCommand());

        assertThat(actual).isSameAs(existing);
        verify(paymentGateway, never()).requestPayment(any());
    }

    @Test
    @DisplayName("동시성 충돌 시 DB 제약 예외를 기존 결제 조회로 흡수한다")
    void startReturnsExistingPaymentWhenInsertConflictOccurs() {
        Payment existing = existingPayment();

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.empty()).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("dup"));

        Payment actual = paymentStartApplicationService.start(startCommand());

        assertThat(actual).isSameAs(existing);
        verify(paymentGateway, never()).requestPayment(any());
    }

    @Test
    @DisplayName("PG 응답 불명확하면 이미 생성된 결제 상태를 유지한다")
    void startKeepsRequestedPaymentWhenRecoveryRequired() {
        Payment saved = existingPayment();

        when(paymentRepository.findByMemberIdAndOrderId(MEMBER_ID, ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(paymentGateway.requestPayment(any())).thenThrow(new PaymentRecoveryRequiredException("request recovery"));

        Payment actual = paymentStartApplicationService.start(startCommand());

        assertThat(actual).isSameAs(saved);
        assertThat(actual.status()).isEqualTo(PaymentStatus.REQUESTED);
        verify(paymentRepository).save(any(Payment.class));
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
