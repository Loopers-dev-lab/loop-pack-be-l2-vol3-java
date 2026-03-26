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
import java.util.Map;

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

        // 1. REQUESTED — 1분 이상 경과 → FAILED
        int requestedCount = entityManager.createNativeQuery(
            "UPDATE payments SET status = 'FAILED', failure_reason = '배치 복구: PG 호출 누락 (1분 초과)' " +
            "WHERE status = 'REQUESTED' AND created_at < NOW() - INTERVAL 1 MINUTE AND deleted_at IS NULL"
        ).executeUpdate();
        log.info("[PaymentRecovery] REQUESTED → FAILED: {}건", requestedCount);

        // 2. PENDING — 5분 이상 경과 → FAILED
        int pendingCount = entityManager.createNativeQuery(
            "UPDATE payments SET status = 'FAILED', failure_reason = '배치 복구: 콜백 미수신 (5분 초과)' " +
            "WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL 5 MINUTE AND deleted_at IS NULL"
        ).executeUpdate();
        log.info("[PaymentRecovery] PENDING(5분+) → FAILED: {}건", pendingCount);

        // 3. UNKNOWN — 일괄 FAILED 처리 (PG 확인 불가 상태)
        int unknownCount = entityManager.createNativeQuery(
            "UPDATE payments SET status = 'FAILED', failure_reason = '배치 복구: UNKNOWN 타임아웃' " +
            "WHERE status = 'UNKNOWN' AND created_at < NOW() - INTERVAL 10 MINUTE AND deleted_at IS NULL"
        ).executeUpdate();
        log.info("[PaymentRecovery] UNKNOWN(10분+) → FAILED: {}건", unknownCount);

        // 4. FAILED 전환된 결제건의 재고 복원 (PENDING → FAILED 건만, order_item 기반)
        if (pendingCount > 0) {
            List<Object[]> failedPayments = entityManager.createNativeQuery(
                "SELECT p.order_id FROM payments p " +
                "WHERE p.status = 'FAILED' AND p.failure_reason LIKE '%콜백 미수신%' " +
                "AND p.updated_at >= NOW() - INTERVAL 1 MINUTE AND p.deleted_at IS NULL"
            ).getResultList();

            for (Object[] row : failedPayments) {
                Long orderId = ((Number) row[0]).longValue();
                int restored = entityManager.createNativeQuery(
                    "UPDATE product p INNER JOIN order_item oi ON p.id = oi.product_id " +
                    "INNER JOIN orders o ON oi.order_id = o.id " +
                    "SET p.stock_quantity = p.stock_quantity + oi.quantity " +
                    "WHERE o.id = :orderId AND p.deleted_at IS NULL"
                ).setParameter("orderId", orderId).executeUpdate();
                log.info("[PaymentRecovery] 재고 복원: orderId={}, items={}", orderId, restored);
            }
        }

        log.info("[PaymentRecovery] 배치 복구 완료: REQUESTED={}, PENDING={}, UNKNOWN={}",
            requestedCount, pendingCount, unknownCount);

        return RepeatStatus.FINISHED;
    }
}
