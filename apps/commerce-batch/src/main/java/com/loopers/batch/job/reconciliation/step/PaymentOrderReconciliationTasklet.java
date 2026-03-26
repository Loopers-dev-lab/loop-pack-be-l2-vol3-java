package com.loopers.batch.job.reconciliation.step;

import com.loopers.batch.job.reconciliation.PaymentOrderReconciliationJobConfig;
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
 * [R2] Payment ↔ Order 대사 — 같은 DB JOIN 쿼리로 불일치 감지.
 *
 * @see <a href="05-payment-resilience.md §10.6">[R2] Payment-Order 대사</a>
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = PaymentOrderReconciliationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class PaymentOrderReconciliationTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[R2-PaymentOrder] 대사 시작");

        // Payment PAID인데 Order가 PAID가 아닌 경우
        List<Object[]> mismatches = entityManager.createNativeQuery(
            "SELECT p.id, p.status, o.status " +
            "FROM payments p JOIN orders o ON p.order_id = o.id " +
            "WHERE ((p.status = 'PAID' AND o.status != 'PAID') " +
            "   OR (p.status = 'FAILED' AND o.status NOT IN ('CANCELLED', 'CREATED'))) " +
            "AND p.deleted_at IS NULL AND o.deleted_at IS NULL"
        ).getResultList();

        if (mismatches.isEmpty()) {
            log.info("[R2-PaymentOrder] 대사 완료: 불일치 0건");
            return RepeatStatus.FINISHED;
        }

        log.warn("[R2-PaymentOrder] 불일치 감지: {}건", mismatches.size());

        for (Object[] row : mismatches) {
            Long paymentId = ((Number) row[0]).longValue();
            String paymentStatus = (String) row[1];
            String orderStatus = (String) row[2];

            entityManager.createNativeQuery(
                "INSERT INTO reconciliation_mismatch (type, payment_id, our_status, external_status, " +
                "detected_at, created_at, updated_at) " +
                "VALUES ('PAYMENT_ORDER', :paymentId, :paymentStatus, :orderStatus, NOW(), NOW(), NOW())"
            ).setParameter("paymentId", paymentId)
             .setParameter("paymentStatus", paymentStatus)
             .setParameter("orderStatus", orderStatus)
             .executeUpdate();

            // Payment PAID + Order CREATED → Order를 PAID로 자동 보정
            if ("PAID".equals(paymentStatus) && "CREATED".equals(orderStatus)) {
                entityManager.createNativeQuery(
                    "UPDATE orders SET status = 'PAID', updated_at = NOW() WHERE id = " +
                    "(SELECT order_id FROM payments WHERE id = :paymentId)"
                ).setParameter("paymentId", paymentId).executeUpdate();
                log.info("[R2-PaymentOrder] 자동 보정: paymentId={} → Order PAID", paymentId);
            }
        }

        log.info("[R2-PaymentOrder] 대사 완료: 불일치 {}건 기록", mismatches.size());
        return RepeatStatus.FINISHED;
    }
}
