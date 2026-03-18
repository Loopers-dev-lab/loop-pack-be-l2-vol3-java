package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentSyncScheduler 단위 테스트")
class PaymentSyncSchedulerTest {

    @InjectMocks
    private PaymentSyncScheduler scheduler;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentApp paymentApp;

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 1000L;
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    private PaymentModel requestedPayment(Long id) {
        PaymentModel payment = PaymentModel.create(ORDER_ID + id, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT);
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.REQUESTED);
        ReflectionTestUtils.setField(payment, "pgTransactionId", "pg-tx-" + id);
        return payment;
    }

    private PaymentModel pendingCbFastFail(Long id) {
        PaymentModel payment = PaymentModel.create(ORDER_ID + id, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", AMOUNT);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    @Nested
    @DisplayName("syncStaleRequestedPayments()")
    class SyncStaleRequested {

        @Test
        @DisplayName("REQUESTED 결제가 있으면 syncFromGateway()를 호출한다")
        void syncStaleRequestedPayments_callsSyncForEach() {
            PaymentModel payment = requestedPayment(1L);
            given(paymentRepository.findStaleRequested(any())).willReturn(List.of(payment));

            scheduler.syncStaleRequestedPayments();

            verify(paymentApp).syncFromGateway(1L, MEMBER_ID);
        }

        @Test
        @DisplayName("대상 결제가 없으면 syncFromGateway()를 호출하지 않는다")
        void syncStaleRequestedPayments_emptyList_noSync() {
            given(paymentRepository.findStaleRequested(any())).willReturn(List.of());

            scheduler.syncStaleRequestedPayments();

            verify(paymentApp, never()).syncFromGateway(anyLong(), anyLong());
        }

        @Test
        @DisplayName("syncFromGateway() 중 OptimisticLockingFailureException 발생 시 재조회 후 COMPLETED면 skip한다")
        void syncStaleRequestedPayments_optimisticLock_alreadyCompleted_skip() {
            PaymentModel payment = requestedPayment(1L);
            given(paymentRepository.findStaleRequested(any())).willReturn(List.of(payment));
            given(paymentApp.syncFromGateway(anyLong(), anyLong())).willThrow(new OptimisticLockingFailureException("lock"));
            PaymentModel completed = requestedPayment(1L);
            ReflectionTestUtils.setField(completed, "status", PaymentStatus.COMPLETED);
            given(paymentRepository.findById(1L)).willReturn(Optional.of(completed));

            assertThatNoException().isThrownBy(() -> scheduler.syncStaleRequestedPayments());
        }

        @Test
        @DisplayName("syncFromGateway() 중 RuntimeException 발생해도 다음 건 처리를 계속한다")
        void syncStaleRequestedPayments_runtimeException_continuesNext() {
            PaymentModel first = requestedPayment(1L);
            PaymentModel second = requestedPayment(2L);
            given(paymentRepository.findStaleRequested(any())).willReturn(List.of(first, second));
            given(paymentApp.syncFromGateway(eq(1L), anyLong())).willThrow(new RuntimeException("error"));

            assertThatNoException().isThrownBy(() -> scheduler.syncStaleRequestedPayments());

            verify(paymentApp).syncFromGateway(2L, MEMBER_ID);
        }
    }

    @Nested
    @DisplayName("expireCbFastFailPendingPayments()")
    class ExpireCbFastFail {

        @Test
        @DisplayName("PENDING + pgTransactionId null 결제가 있으면 forceFailPayment()를 호출한다")
        void expireCbFastFail_callsForceFailForEach() {
            PaymentModel payment = pendingCbFastFail(1L);
            given(paymentRepository.findExpiredCbFastFail(any())).willReturn(List.of(payment));

            scheduler.expireCbFastFailPendingPayments();

            verify(paymentApp).forceFailPayment(1L);
        }

        @Test
        @DisplayName("대상 결제가 없으면 forceFailPayment()를 호출하지 않는다")
        void expireCbFastFail_emptyList_noAction() {
            given(paymentRepository.findExpiredCbFastFail(any())).willReturn(List.of());

            scheduler.expireCbFastFailPendingPayments();

            verify(paymentApp, never()).forceFailPayment(anyLong());
        }

        @Test
        @DisplayName("forceFailPayment() 중 OptimisticLockingFailureException 발생 시 재조회 후 FAILED면 skip한다")
        void expireCbFastFail_optimisticLock_alreadyFailed_skip() {
            PaymentModel payment = pendingCbFastFail(1L);
            given(paymentRepository.findExpiredCbFastFail(any())).willReturn(List.of(payment));
            given(paymentApp.forceFailPayment(anyLong())).willThrow(new OptimisticLockingFailureException("lock"));
            PaymentModel failed = pendingCbFastFail(1L);
            ReflectionTestUtils.setField(failed, "status", PaymentStatus.FAILED);
            given(paymentRepository.findById(1L)).willReturn(Optional.of(failed));

            assertThatNoException().isThrownBy(() -> scheduler.expireCbFastFailPendingPayments());
        }

        @Test
        @DisplayName("복수 건 처리 중 1건 실패해도 나머지 건을 계속 처리한다")
        void expireCbFastFail_oneFailure_continuesRest() {
            PaymentModel first = pendingCbFastFail(1L);
            PaymentModel second = pendingCbFastFail(2L);
            given(paymentRepository.findExpiredCbFastFail(any())).willReturn(List.of(first, second));
            given(paymentApp.forceFailPayment(1L)).willThrow(new RuntimeException("error"));

            assertThatNoException().isThrownBy(() -> scheduler.expireCbFastFailPendingPayments());

            verify(paymentApp).forceFailPayment(2L);
        }
    }

    @Nested
    @DisplayName("cutoff 계산")
    class CutoffCalculation {

        @Test
        @DisplayName("stale-requested-minutes=10이면 cutoff가 now - 10분으로 findStaleRequested를 호출한다")
        void syncStaleRequested_cutoffIsNowMinusStaleMinutes() {
            ReflectionTestUtils.setField(scheduler, "staleRequestedMinutes", 10);
            given(paymentRepository.findStaleRequested(any())).willReturn(List.of());

            scheduler.syncStaleRequestedPayments();

            ArgumentCaptor<java.time.ZonedDateTime> captor = ArgumentCaptor.forClass(java.time.ZonedDateTime.class);
            verify(paymentRepository).findStaleRequested(captor.capture());
            java.time.ZonedDateTime cutoff = captor.getValue();
            assertThat(cutoff).isBefore(java.time.ZonedDateTime.now().minusMinutes(9));
            assertThat(cutoff).isAfter(java.time.ZonedDateTime.now().minusMinutes(11));
        }

        @Test
        @DisplayName("cb-fast-fail-expiry-minutes=10이면 cutoff가 now - 10분으로 findExpiredCbFastFail을 호출한다")
        void expireCbFastFail_cutoffIsNowMinusExpiryMinutes() {
            ReflectionTestUtils.setField(scheduler, "cbFastFailExpiryMinutes", 10);
            given(paymentRepository.findExpiredCbFastFail(any())).willReturn(List.of());

            scheduler.expireCbFastFailPendingPayments();

            ArgumentCaptor<java.time.ZonedDateTime> captor = ArgumentCaptor.forClass(java.time.ZonedDateTime.class);
            verify(paymentRepository).findExpiredCbFastFail(captor.capture());
            java.time.ZonedDateTime cutoff = captor.getValue();
            assertThat(cutoff).isBefore(java.time.ZonedDateTime.now().minusMinutes(9));
            assertThat(cutoff).isAfter(java.time.ZonedDateTime.now().minusMinutes(11));
        }
    }
}
