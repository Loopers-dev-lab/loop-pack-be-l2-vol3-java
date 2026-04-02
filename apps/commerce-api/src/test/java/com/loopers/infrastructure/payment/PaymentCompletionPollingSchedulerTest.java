package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentUseCase;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionPollingSchedulerTest {

    private static final String MEMBER_ID = "paymentmember";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentUseCase paymentUseCase;

    @InjectMocks
    private PaymentCompletionPollingScheduler paymentCompletionPollingScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentCompletionPollingScheduler, "requestedMinAgeMs", 1000L);
    }

    @Test
    @DisplayName("대상 상태의 결제는 reconcile 을 호출한다")
    void pollPendingPaymentsReconcilesEligiblePayments() {
        Payment oldRequested = payment(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                PaymentStatus.REQUESTED,
                ZonedDateTime.now().minusSeconds(5)
        );
        Payment cancelRequested = payment(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                PaymentStatus.CANCEL_REQUESTED,
                ZonedDateTime.now()
        );
        Payment cancelReconcileRequired = payment(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                PaymentStatus.CANCEL_RECONCILE_REQUIRED,
                ZonedDateTime.now()
        );
        when(paymentRepository.findAllByStatusIn(anyList()))
                .thenReturn(List.of(oldRequested, cancelRequested, cancelReconcileRequired));

        paymentCompletionPollingScheduler.pollPendingPayments();

        verify(paymentUseCase).reconcile(MEMBER_ID, oldRequested.orderId());
        verify(paymentUseCase).reconcile(MEMBER_ID, cancelRequested.orderId());
        verify(paymentUseCase).reconcile(MEMBER_ID, cancelReconcileRequired.orderId());
    }

    @Test
    @DisplayName("최근 REQUESTED 결제는 아직 reconcile 대상에서 제외한다")
    void pollPendingPaymentsSkipsRecentRequestedPayment() {
        Payment recentRequested = payment(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                PaymentStatus.REQUESTED,
                ZonedDateTime.now()
        );
        when(paymentRepository.findAllByStatusIn(anyList())).thenReturn(List.of(recentRequested));

        paymentCompletionPollingScheduler.pollPendingPayments();

        verify(paymentUseCase, never()).reconcile(MEMBER_ID, recentRequested.orderId());
    }

    @Test
    @DisplayName("한 결제 reconcile 실패가 다음 결제 처리를 막지 않는다")
    void pollPendingPaymentsContinuesAfterReconcileFailure() {
        Payment first = payment(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                PaymentStatus.CANCEL_REQUESTED,
                ZonedDateTime.now()
        );
        Payment second = payment(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                PaymentStatus.CANCEL_RECONCILE_REQUIRED,
                ZonedDateTime.now()
        );
        when(paymentRepository.findAllByStatusIn(anyList())).thenReturn(List.of(first, second));
        when(paymentUseCase.reconcile(MEMBER_ID, first.orderId()))
                .thenThrow(new CoreException(ErrorType.INTERNAL_ERROR, "boom"));

        paymentCompletionPollingScheduler.pollPendingPayments();

        verify(paymentUseCase).reconcile(MEMBER_ID, first.orderId());
        verify(paymentUseCase).reconcile(MEMBER_ID, second.orderId());
    }

    @Test
    @DisplayName("조회 저장소 오류가 나면 reconcile 을 호출하지 않고 종료한다")
    void pollPendingPaymentsSkipsWhenRepositoryLookupFails() {
        when(paymentRepository.findAllByStatusIn(anyList())).thenThrow(new IllegalStateException("db down"));

        paymentCompletionPollingScheduler.pollPendingPayments();

        verifyNoInteractions(paymentUseCase);
    }

    private Payment payment(UUID orderId, PaymentStatus status, ZonedDateTime updatedAt) {
        String transactionKey = status == PaymentStatus.CANCEL_REQUESTED ? "trx-" + orderId : null;
        String reason = status == PaymentStatus.CANCEL_RECONCILE_REQUIRED ? "retry later" : null;

        return new Payment(
                UUID.randomUUID(),
                MEMBER_ID,
                orderId,
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                15000,
                status,
                transactionKey,
                reason,
                updatedAt.minusMinutes(1),
                updatedAt,
                null
        );
    }
}
