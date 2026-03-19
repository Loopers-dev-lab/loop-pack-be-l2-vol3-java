package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgOrderStatusResponse;
import com.loopers.domain.payment.PgPaymentStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 콜백 미수신 결제 복구 스케줄러.
 *
 * PENDING 상태로 일정 시간이 지난 Payment를 찾아서:
 * 1. PG에 결제 상태를 직접 조회
 * 2. 결과에 따라 handleCallback으로 처리 (콜백과 동일한 로직 재사용)
 * 3. 너무 오래된 건은 최종 PG 조회 후 타임아웃 처리
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentRecoveryScheduler {

    private static final int POLL_AFTER_SECONDS = 30;     // 생성 후 30초 경과한 건부터 폴링
    private static final int TIMEOUT_AFTER_SECONDS = 300;  // 생성 후 5분 경과 시 최종 타임아웃

    private final PaymentService paymentService;
    private final PgClient pgClient;
    private final PaymentResultHandler resultHandler;

    /**
     * 30초 간격으로 미처리 결제를 복구.
     *
     * fixedDelay: 이전 실행 완료 후 30초 대기 (실행 중 겹치지 않음)
     * initialDelay: 애플리케이션 기동 후 60초 대기 (기동 안정화)
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void recoverPendingPayments() {
        ZonedDateTime pollThreshold = ZonedDateTime.now().minusSeconds(POLL_AFTER_SECONDS);
        List<Payment> pendingPayments = paymentService.findAllRequested(pollThreshold);

        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("미처리 결제 복구 시작: {}건", pendingPayments.size());

        for (Payment payment : pendingPayments) {
            try {
                recoverPayment(payment);
            } catch (Exception e) {
                // 개별 건 실패가 전체 스케줄러를 중단시키지 않도록 격리
                log.error("결제 복구 실패: paymentId={}, error={}", payment.getId(), e.getMessage());
            }
        }
    }

    private void recoverPayment(Payment payment) {
        // 이미 처리되었으면 스킵 (다른 스레드가 처리했을 수 있음)
        if (payment.isTerminal()) {
            return;
        }

        // transactionKey가 없는 경우: orderId로 PG 측 결제 존재 여부를 확인
        // (응답 타임아웃으로 transactionKey를 못 받았지만, PG에는 결제가 접수되었을 수 있음)
        if (payment.getTransactionKey() == null) {
            recoverPaymentWithoutTransactionKey(payment);
            return;
        }

        // PG에 결제 상태 직접 조회 (Resilience4j pgPaymentStatus 인스턴스가 보호)
        PgPaymentStatusResponse statusResponse = pgClient.getPaymentStatus(payment.getUserId(), payment.getTransactionKey());

        if (statusResponse.isSuccess()) {
            resultHandler.handleCallback(payment.getTransactionKey(), "SUCCESS", null);
        } else if (statusResponse.isFailed()) {
            resultHandler.handleCallback(payment.getTransactionKey(), "FAILED", statusResponse.reason());
        } else if (isExpired(payment)) {
            // PG에서 아직 PENDING이지만 5분 초과 → 최종 타임아웃
            // 타임아웃 직전에 PG 조회를 했으므로, 실제로 PG에서 SUCCESS인데 우리가 TIMEOUT 처리하는 유령 결제를 방지
            log.warn("최종 타임아웃 처리: paymentId={}, orderId={}, pgStatus={}",
                    payment.getId(), payment.getOrderId(), statusResponse.status());
            resultHandler.handleFinalTimeout(payment.getId(), payment.getOrderId(), "결제 최종 타임아웃 (5분 초과)");
        }
        // PENDING이면서 5분 미만 → 다음 주기에 재확인
    }

    // transactionKey 미수신 건: orderId로 PG 조회하여 복구 시도
    private void recoverPaymentWithoutTransactionKey(Payment payment) {
        PgOrderStatusResponse orderStatus = pgClient.getPaymentStatusByOrderId(
                payment.getUserId(), String.valueOf(payment.getOrderId()));

        if (orderStatus.hasTransactions()) {
            // PG에 결제가 존재 → transactionKey를 복구하고 결과 처리
            // pg-simluator 로직상 하나의 주문에 여러 결제 요청이 올 수 있기 때문에, 제일 최근 결제건을 선택
            PgPaymentStatusResponse latestTx = orderStatus.latestTransaction();
            log.info("transactionKey 복구 (orderId 기준 PG 조회): paymentId={}, transactionKey={}, status={}",
                    payment.getId(), latestTx.transactionKey(), latestTx.status());

            // transactionKey를 먼저 할당한 후, 콜백 처리 로직 재사용
            resultHandler.handlePgAccepted(payment.getId(), latestTx.transactionKey());

            if (latestTx.isSuccess()) {
                resultHandler.handleCallback(latestTx.transactionKey(), "SUCCESS", null);
            } else if (latestTx.isFailed()) {
                resultHandler.handleCallback(latestTx.transactionKey(), "FAILED", latestTx.reason());
            }
            // PENDING이면 다음 주기에 transactionKey 기반으로 재확인
        } else if (isExpired(payment)) {
            // PG에도 결제 없음 + 5분 경과 → 요청 자체가 도달하지 않은 것 확정
            log.warn("transactionKey 미할당 + PG 조회 결과 없음 → 최종 타임아웃: paymentId={}", payment.getId());
            resultHandler.handleFinalTimeout(payment.getId(), payment.getOrderId(), "결제 최종 타임아웃 (PG 미접수 확인)");
        } else {
            log.debug("transactionKey 미할당 + PG 미접수 — 다음 주기에 재확인: paymentId={}", payment.getId());
        }
    }

    private boolean isExpired(Payment payment) {
        return payment.getCreatedAt()
                .plusSeconds(TIMEOUT_AFTER_SECONDS)
                .isBefore(ZonedDateTime.now());
    }
}
