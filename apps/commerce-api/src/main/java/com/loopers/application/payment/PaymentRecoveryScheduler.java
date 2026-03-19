package com.loopers.application.payment;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.OrderTransactionResult;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.TransactionDetailResult;
import com.loopers.domain.payment.TransactionResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 상태 점검 및 복구 스케줄러.
 *
 * <p>1분 주기로 PENDING/READY 상태가 일정 시간 경과한 결제를 PG에 조회하여
 * 상태를 동기화하거나 복구한다. 개별 건 처리 실패 시 해당 건을 스킵하고 나머지를 계속 처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private static final long SYNC_INTERVAL_MS = 60_000;
    private static final long PENDING_THRESHOLD_MINUTES = 3;
    private static final long READY_THRESHOLD_MINUTES = 3;

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    private final PaymentProcessor paymentProcessor;
    private final OrderService orderService;

    /**
     * PENDING 상태가 기준 시간을 초과한 결제를 PG에 조회하여 상태를 동기화한다.
     */
    @Scheduled(fixedDelay = SYNC_INTERVAL_MS)
    public void syncPendingPayments() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(PENDING_THRESHOLD_MINUTES);
        List<Payment> pendingPayments = paymentService.getPendingPaymentsBefore(threshold);

        for (Payment payment : pendingPayments) {
            try {
                syncPendingPayment(payment);
            } catch (Exception e) {
                log.warn("PENDING 결제 동기화 실패 [transactionKey={}]", payment.getTransactionKey(), e);
            }
        }
    }

    /**
     * READY 상태로 방치된 결제를 PG에 조회하여 복구한다.
     *
     * <p>PG 요청 타임아웃으로 transactionKey가 없는 결제를 orderId 기반으로 PG에 조회하여,
     * {@link PaymentProcessor}에 복구를 위임한다.</p>
     */
    @Scheduled(fixedDelay = SYNC_INTERVAL_MS)
    public void recoverReadyPayments() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(READY_THRESHOLD_MINUTES);
        List<Payment> readyPayments = paymentService.getReadyPaymentsBefore(threshold);

        for (Payment payment : readyPayments) {
            try {
                recoverReadyPayment(payment);
            } catch (Exception e) {
                log.warn("READY 결제 복구 실패 [paymentId={}]", payment.getId(), e);
            }
        }
    }

    /**
     * 개별 PENDING 결제를 PG에 조회하여 상태를 동기화한다.
     *
     * <p>PG 조회 결과가 SUCCESS/FAILED이면 {@link HandlePaymentCallbackUseCase}에 위임하고,
     * 여전히 PENDING이거나 알 수 없는 상태이면 스킵한다.</p>
     */
    private void syncPendingPayment(Payment payment) {
        TransactionDetailResult result = paymentGateway.getTransaction(
                payment.getUserId(),
                payment.getTransactionKey()
        );

        PaymentStatus status = resolveStatus(result.status());
        if (status == null || status == PaymentStatus.PENDING) {
            return;
        }

        handlePaymentCallbackUseCase.execute(
                new PaymentCallbackCommand(result.transactionKey(), status, result.reason())
        );
    }

    /**
     * 개별 READY 결제를 PG에 조회하여 복구한다.
     *
     * <p>orderId 기반으로 PG 거래를 조회하여 SUCCESS 거래 수에 따라 분기한다:
     * <ul>
     *   <li>SUCCESS 0건 — 거래 없음으로 판단하여 실패 처리</li>
     *   <li>SUCCESS 1건 — 해당 거래로 결제 확정 + 후속 처리</li>
     *   <li>SUCCESS 2건 이상 — 수동 확인 필요로 판단하여 스킵 (에러 로그)</li>
     * </ul></p>
     */
    private void recoverReadyPayment(Payment payment) {
        Order order = orderService.getById(payment.getOrderId());
        OrderTransactionResult orderTransactions = paymentGateway.getTransactionsByOrder(
                payment.getUserId(),
                order.getOrderKey()
        );

        List<TransactionResult> transactions = orderTransactions.transactions();
        List<TransactionResult> successTransactions = transactions.stream()
                .filter(txn -> "SUCCESS".equalsIgnoreCase(txn.status()))
                .toList();

        if (successTransactions.isEmpty()) {
            paymentProcessor.recoverWithoutTransaction(payment.getId(), "PG 결제 요청 타임아웃으로 거래 없음");
        } else if (successTransactions.size() == 1) {
            TransactionResult txn = successTransactions.get(0);
            paymentProcessor.recoverWithTransaction(
                    payment.getId(),
                    txn.transactionKey(),
                    PaymentStatus.SUCCESS,
                    txn.reason()
            );
        } else {
            log.error("READY 결제 복구 스킵: SUCCESS 거래가 {}건 존재하여 수동 확인 필요 [paymentId={}, orderKey={}]",
                    successTransactions.size(),
                    payment.getId(),
                    order.getOrderKey()
            );
        }
    }

    /**
     * PG 상태 문자열을 {@link PaymentStatus}로 변환한다.
     *
     * @param pgStatus PG 응답 상태 문자열
     * @return 매핑된 PaymentStatus, 알 수 없는 상태이면 null
     */
    private PaymentStatus resolveStatus(String pgStatus) {
        try {
            return PaymentStatus.valueOf(pgStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("PG에서 알 수 없는 결제 상태 수신: {}", pgStatus);
            return null;
        }
    }
}
