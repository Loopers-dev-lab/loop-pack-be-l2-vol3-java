package com.loopers.batch.job.paymentrecovery.step;

import com.loopers.batch.job.paymentrecovery.PaymentRecoveryJobConfig;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 결제 복구 배치 — REQUESTED/PENDING/UNKNOWN 상태 결제건 복구.
 *
 * <p>복구 기준:</p>
 * <ul>
 *   <li>REQUESTED: 생성 후 1분 경과 → FAILED 처리</li>
 *   <li>PENDING: 생성 후 5분 초과 → FAILED 처리 + 재고 복원</li>
 *   <li>UNKNOWN: PG 상태 확인 불가 → FAILED 처리</li>
 * </ul>
 *
 * @see <a href="05-payment-resilience.md §10.4">배치 복구</a>
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = PaymentRecoveryJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class PaymentRecoveryTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[PaymentRecovery] 배치 복구 시작");

        int requestedCount = recoverRequested();
        int pendingCount = recoverPending();
        int unknownCount = recoverUnknown();

        log.info("[PaymentRecovery] 배치 복구 완료: REQUESTED={}, PENDING={}, UNKNOWN={}",
            requestedCount, pendingCount, unknownCount);

        return RepeatStatus.FINISHED;
    }

    private int recoverRequested() {
        List<Number> ids = entityManager.createNativeQuery(
            "SELECT id FROM payments WHERE status = 'REQUESTED' " +
            "AND created_at < NOW() - INTERVAL 1 MINUTE AND deleted_at IS NULL"
        ).getResultList();

        if (ids.isEmpty()) return 0;

        List<Long> targetIds = ids.stream().map(Number::longValue).toList();

        entityManager.createNativeQuery(
            "INSERT INTO payment_status_history (payment_id, from_status, to_status, reason, detail, created_at, updated_at) " +
            "SELECT id, 'REQUESTED', 'FAILED', 'BATCH_RECOVERY', '배치 복구: PG 호출 누락 (1분 초과)', NOW(), NOW() " +
            "FROM payments WHERE id IN :ids AND status = 'REQUESTED' AND deleted_at IS NULL"
        ).setParameter("ids", targetIds).executeUpdate();

        int count = entityManager.createNativeQuery(
            "UPDATE payments SET status = 'FAILED', failure_reason = '배치 복구: PG 호출 누락 (1분 초과)' " +
            "WHERE id IN :ids AND status = 'REQUESTED' AND deleted_at IS NULL"
        ).setParameter("ids", targetIds).executeUpdate();

        log.info("[PaymentRecovery] REQUESTED → FAILED: {}건", count);
        return count;
    }

    @SuppressWarnings("unchecked")
    private int recoverPending() {
        List<Number> ids = entityManager.createNativeQuery(
            "SELECT id FROM payments WHERE status = 'PENDING' " +
            "AND created_at < NOW() - INTERVAL 5 MINUTE AND deleted_at IS NULL"
        ).getResultList();

        if (ids.isEmpty()) return 0;

        List<Long> targetIds = ids.stream().map(Number::longValue).toList();

        entityManager.createNativeQuery(
            "INSERT INTO payment_status_history (payment_id, from_status, to_status, reason, detail, created_at, updated_at) " +
            "SELECT id, 'PENDING', 'FAILED', 'BATCH_RECOVERY', '배치 복구: 콜백 미수신 (5분 초과)', NOW(), NOW() " +
            "FROM payments WHERE id IN :ids AND status = 'PENDING' AND deleted_at IS NULL"
        ).setParameter("ids", targetIds).executeUpdate();

        int count = entityManager.createNativeQuery(
            "UPDATE payments SET status = 'FAILED', failure_reason = '배치 복구: 콜백 미수신 (5분 초과)' " +
            "WHERE id IN :ids AND status = 'PENDING' AND deleted_at IS NULL"
        ).setParameter("ids", targetIds).executeUpdate();

        log.info("[PaymentRecovery] PENDING(5분+) → FAILED: {}건", count);

        // FAILED 전환된 결제건의 재고 복원
        if (count > 0) {
            restoreStockForFailedPayments(targetIds);
        }

        return count;
    }

    private int recoverUnknown() {
        List<Number> ids = entityManager.createNativeQuery(
            "SELECT id FROM payments WHERE status = 'UNKNOWN' " +
            "AND created_at < NOW() - INTERVAL 10 MINUTE AND deleted_at IS NULL"
        ).getResultList();

        if (ids.isEmpty()) return 0;

        List<Long> targetIds = ids.stream().map(Number::longValue).toList();

        entityManager.createNativeQuery(
            "INSERT INTO payment_status_history (payment_id, from_status, to_status, reason, detail, created_at, updated_at) " +
            "SELECT id, 'UNKNOWN', 'FAILED', 'BATCH_RECOVERY', '배치 복구: UNKNOWN 타임아웃', NOW(), NOW() " +
            "FROM payments WHERE id IN :ids AND status = 'UNKNOWN' AND deleted_at IS NULL"
        ).setParameter("ids", targetIds).executeUpdate();

        int count = entityManager.createNativeQuery(
            "UPDATE payments SET status = 'FAILED', failure_reason = '배치 복구: UNKNOWN 타임아웃' " +
            "WHERE id IN :ids AND status = 'UNKNOWN' AND deleted_at IS NULL"
        ).setParameter("ids", targetIds).executeUpdate();

        log.info("[PaymentRecovery] UNKNOWN(10분+) → FAILED: {}건", count);
        return count;
    }

    @SuppressWarnings("unchecked")
    private void restoreStockForFailedPayments(List<Long> paymentIds) {
        List<Number> orderIds = entityManager.createNativeQuery(
            "SELECT order_id FROM payments WHERE id IN :ids AND deleted_at IS NULL"
        ).setParameter("ids", paymentIds).getResultList();

        for (Number orderIdNum : orderIds) {
            Long orderId = orderIdNum.longValue();
            int restored = entityManager.createNativeQuery(
                "UPDATE product p INNER JOIN order_item oi ON p.id = oi.product_id " +
                "INNER JOIN orders o ON oi.order_id = o.id " +
                "SET p.stock_quantity = p.stock_quantity + oi.quantity " +
                "WHERE o.id = :orderId AND p.deleted_at IS NULL"
            ).setParameter("orderId", orderId).executeUpdate();
            log.info("[PaymentRecovery] 재고 복원: orderId={}, items={}", orderId, restored);
        }
    }
}
