package com.loopers.batch.job.reconciliation.step;

import com.loopers.batch.job.reconciliation.PgPaymentReconciliationJobConfig;
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
 * [R1] PG ↔ Payment 대사 — PG API 호출이 필요하므로 실제로는 PG 연동 구현 후 완성.
 * 현재는 Payment 상태 기준 reconciliation_mismatch 준비만 수행.
 *
 * @see <a href="05-payment-resilience.md §10.6">[R1] PG 대사</a>
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = PgPaymentReconciliationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class PgPaymentReconciliationTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[R1-PgPayment] 대사 시작: PAID/FAILED 결제건 PG 상태 대조");

        // PG API 대조 대상: 최근 24시간 내 PAID/FAILED 건
        List<Object[]> payments = entityManager.createNativeQuery(
            "SELECT id, status, transaction_key, pg_provider FROM payments " +
            "WHERE status IN ('PAID', 'FAILED') " +
            "AND updated_at > NOW() - INTERVAL 24 HOUR " +
            "AND deleted_at IS NULL"
        ).getResultList();

        log.info("[R1-PgPayment] 대사 대상: {}건", payments.size());

        // 실제 PG API 호출은 Phase 6 (Multi-PG) 이후에 완성
        // 현재는 대사 인프라만 준비 (불일치 감지 → reconciliation_mismatch INSERT)
        // TODO: PG API 연동 후 각 건의 PG 상태와 대조

        log.info("[R1-PgPayment] 대사 완료 (PG API 연동 대기)");
        return RepeatStatus.FINISHED;
    }
}
