package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * PENDING 상태로 장시간 머문 결제건을 주기적으로 PG에 조회해 상태를 동기화한다.
 *
 * <p>콜백 미수신(네트워크 유실, PG 장애, 서킷브레이커 OPEN 중 대량 발생 등)으로
 * PENDING에 고착된 결제건을 자동으로 복구하기 위한 안전망이다.
 *
 * <p>배치 크기(batchSize)를 제한해 PG API에 과부하를 주지 않으며,
 * 건별 예외를 격리해 한 건의 실패가 전체 배치를 중단시키지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSyncScheduler {

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PaymentTxService paymentTxService;

    @Value("${payment.sync.pending-threshold-minutes:10}")
    private int pendingThresholdMinutes;

    @Value("${payment.sync.batch-size:50}")
    private int batchSize;

    /**
     * fixedDelay: 이전 실행 완료 후 N ms 뒤에 다음 실행.
     * cron 방식과 달리 실행 시간이 길어져도 중복 실행되지 않는다.
     */
    @Scheduled(fixedDelayString = "${payment.sync.fixed-delay-ms:300000}") // 기본 5분
    public void syncPendingPayments() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(pendingThresholdMinutes);
        List<Payment> pendingPayments = paymentRepository.findAllPendingBefore(threshold);

        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("[PaymentSyncScheduler] PENDING 복구 대상: {}건 ({}분 초과)", pendingPayments.size(), pendingThresholdMinutes);

        List<Payment> targets = pendingPayments.size() > batchSize
            ? pendingPayments.subList(0, batchSize)
            : pendingPayments;

        int successCount = 0;
        int failCount = 0;

        for (Payment payment : targets) {
            try {
                boolean synced = syncSingle(payment);
                if (synced) successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("[PaymentSyncScheduler] orderId={} 동기화 실패: {}", payment.getOrderId(), e.getMessage());
            }
        }

        log.info("[PaymentSyncScheduler] 완료 — 동기화 성공: {}건, 실패: {}건", successCount, failCount);
    }

    /**
     * @return PG 조회 결과가 있어 상태 전이가 발생한 경우 true
     */
    private boolean syncSingle(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }

        return pgClient.getPaymentByOrderId(payment.getUserId(), payment.getOrderId())
            .map(info -> {
                paymentTxService.processPaymentResult(
                    payment.getId(), payment.getOrderId(), info.transactionKey(), info.status()
                );
                log.info("[PaymentSyncScheduler] orderId={} → {}", payment.getOrderId(), info.status());
                return true;
            })
            .orElseGet(() -> {
                log.debug("[PaymentSyncScheduler] orderId={} PG 조회 결과 없음, PENDING 유지", payment.getOrderId());
                return false;
            });
    }
}
