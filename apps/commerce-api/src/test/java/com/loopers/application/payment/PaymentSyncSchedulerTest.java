package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.PgPaymentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentSyncSchedulerTest {

    private PaymentRepository paymentRepository;
    private PgClient pgClient;
    private PaymentTxService paymentTxService;
    private PaymentSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        pgClient = mock(PgClient.class);
        paymentTxService = mock(PaymentTxService.class);
        scheduler = new PaymentSyncScheduler(paymentRepository, pgClient, paymentTxService);
        ReflectionTestUtils.setField(scheduler, "pendingThresholdMinutes", 10);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
    }

    @DisplayName("syncPendingPayments: PENDING 결제건을 PG 조회 후 processPaymentResult를 호출한다")
    @Test
    void syncPendingPayments_success() {
        Payment payment = mockPayment(100L, 1L, PaymentStatus.PENDING);
        when(paymentRepository.findAllPendingBefore(any())).thenReturn(List.of(payment));
        when(pgClient.getPaymentByOrderId(1L, 100L))
            .thenReturn(Optional.of(new PgPaymentInfo("txId1", "000100", "SUCCESS", 5000L)));

        scheduler.syncPendingPayments();

        verify(paymentTxService).processPaymentResult(1L, 100L, "txId1", "SUCCESS");
    }

    @DisplayName("syncPendingPayments: PG 조회 결과가 없으면 PENDING을 유지한다 (processPaymentResult 미호출)")
    @Test
    void syncPendingPayments_pgNotFound_stayPending() {
        Payment payment = mockPayment(100L, 1L, PaymentStatus.PENDING);
        when(paymentRepository.findAllPendingBefore(any())).thenReturn(List.of(payment));
        when(pgClient.getPaymentByOrderId(1L, 100L)).thenReturn(Optional.empty());

        scheduler.syncPendingPayments();

        verify(paymentTxService, never()).processPaymentResult(anyLong(), anyLong(), anyString(), anyString());
    }

    @DisplayName("syncPendingPayments: PENDING 건이 없으면 아무것도 하지 않는다")
    @Test
    void syncPendingPayments_noPending_noop() {
        when(paymentRepository.findAllPendingBefore(any())).thenReturn(List.of());

        scheduler.syncPendingPayments();

        verify(pgClient, never()).getPaymentByOrderId(anyLong(), anyLong());
    }

    @DisplayName("syncPendingPayments: 한 건이 실패해도 다음 건을 계속 처리한다 (개별 실패 격리)")
    @Test
    void syncPendingPayments_individualFailureIsolation() {
        Payment payment1 = mockPayment(100L, 1L, PaymentStatus.PENDING);
        Payment payment2 = mockPayment(200L, 2L, PaymentStatus.PENDING);
        when(paymentRepository.findAllPendingBefore(any())).thenReturn(List.of(payment1, payment2));

        // 첫 번째 건은 PG 조회 시 예외 발생
        when(pgClient.getPaymentByOrderId(1L, 100L)).thenThrow(new RuntimeException("PG 장애"));
        // 두 번째 건은 정상
        when(pgClient.getPaymentByOrderId(2L, 200L))
            .thenReturn(Optional.of(new PgPaymentInfo("txId2", "000200", "SUCCESS", 3000L)));

        scheduler.syncPendingPayments();

        // 두 번째 건은 정상 처리됨
        verify(paymentTxService).processPaymentResult(2L, 200L, "txId2", "SUCCESS");
    }

    @DisplayName("syncPendingPayments: batchSize를 초과하면 초과분은 처리하지 않는다")
    @Test
    void syncPendingPayments_batchSizeLimit() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 1);

        Payment payment1 = mockPayment(100L, 1L, PaymentStatus.PENDING);
        Payment payment2 = mockPayment(200L, 2L, PaymentStatus.PENDING);
        when(paymentRepository.findAllPendingBefore(any())).thenReturn(List.of(payment1, payment2));
        when(pgClient.getPaymentByOrderId(1L, 100L))
            .thenReturn(Optional.of(new PgPaymentInfo("txId1", "000100", "SUCCESS", 5000L)));

        scheduler.syncPendingPayments();

        // batchSize=1이므로 첫 번째 건만 처리
        verify(pgClient).getPaymentByOrderId(1L, 100L);
        verify(pgClient, never()).getPaymentByOrderId(2L, 200L);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Payment mockPayment(Long orderId, Long userId, PaymentStatus status) {
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(userId);
        when(payment.getOrderId()).thenReturn(orderId);
        when(payment.getUserId()).thenReturn(userId);
        when(payment.getStatus()).thenReturn(status);
        when(payment.getAmount()).thenReturn(BigDecimal.valueOf(5000));
        return payment;
    }
}
