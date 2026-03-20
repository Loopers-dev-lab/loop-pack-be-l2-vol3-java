package com.loopers.batch.job.reconciliation.step;

import com.loopers.batch.job.reconciliation.PaymentCouponReconciliationJobConfig;
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
 * [R3] Payment ↔ Coupon 대사 — 쿠폰 복원 누락 감지 + 자동 복원.
 *
 * @see <a href="05-payment-resilience.md §10.6">[R3] Payment-Coupon 대사</a>
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = PaymentCouponReconciliationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class PaymentCouponReconciliationTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[R3-PaymentCoupon] 대사 시작");

        // Payment FAILED인데 CouponIssue가 아직 USED인 경우 = 쿠폰 복원 누락
        List<Object[]> mismatches = entityManager.createNativeQuery(
            "SELECT p.id, o.coupon_issue_id, ci.status " +
            "FROM payments p " +
            "JOIN orders o ON p.order_id = o.id " +
            "JOIN coupon_issue ci ON o.coupon_issue_id = ci.id " +
            "WHERE p.status IN ('FAILED', 'CANCELLED') " +
            "AND ci.status = 'USED' " +
            "AND p.deleted_at IS NULL AND o.deleted_at IS NULL"
        ).getResultList();

        if (mismatches.isEmpty()) {
            log.info("[R3-PaymentCoupon] 대사 완료: 불일치 0건");
            return RepeatStatus.FINISHED;
        }

        log.warn("[R3-PaymentCoupon] 쿠폰 복원 누락 감지: {}건", mismatches.size());

        for (Object[] row : mismatches) {
            Long paymentId = ((Number) row[0]).longValue();
            Long couponIssueId = ((Number) row[1]).longValue();

            // 쿠폰 자동 복원
            int updated = entityManager.createNativeQuery(
                "UPDATE coupon_issue SET status = 'AVAILABLE', used_order_id = NULL " +
                "WHERE id = :couponIssueId AND status = 'USED'"
            ).setParameter("couponIssueId", couponIssueId).executeUpdate();

            if (updated > 0) {
                log.info("[R3-PaymentCoupon] 쿠폰 자동 복원: couponIssueId={}, paymentId={}",
                    couponIssueId, paymentId);
            }

            // 불일치 기록
            entityManager.createNativeQuery(
                "INSERT INTO reconciliation_mismatch (type, payment_id, our_status, external_status, " +
                "detected_at, resolution, created_at, updated_at, note) " +
                "VALUES ('PAYMENT_COUPON', :paymentId, 'FAILED', 'USED', NOW(), 'AUTO_FIXED', NOW(), NOW(), " +
                "'쿠폰 복원 누락 자동 보정')"
            ).setParameter("paymentId", paymentId).executeUpdate();
        }

        log.info("[R3-PaymentCoupon] 대사 완료: {}건 자동 복원", mismatches.size());
        return RepeatStatus.FINISHED;
    }
}
