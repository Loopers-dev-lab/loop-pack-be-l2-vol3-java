package com.loopers.batch.job.metricsreconcile.step;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class MetricsReconcileTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 1단계: likes 테이블 기준 → product_metrics.like_count 보정
        log.info("[MetricsReconcile] 1단계: like_count 대사 시작");
        int likeCorrected = entityManager.createNativeQuery(
            "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) "
                + "SELECT l.product_id, COUNT(*), 0, 0, 0, NOW(6) FROM likes l GROUP BY l.product_id "
                + "ON DUPLICATE KEY UPDATE like_count = VALUES(like_count), updated_at = NOW(6)"
        ).executeUpdate();
        log.info("[MetricsReconcile] 1단계 완료 — 대사 행 수: {}", likeCorrected);

        // 2단계: product_metrics.like_count → Product.like_count 비정규화 보정
        log.info("[MetricsReconcile] 2단계: Product.like_count 드리프트 보정 시작");
        int productCorrected = entityManager.createNativeQuery(
            "UPDATE product p JOIN product_metrics pm ON p.id = pm.product_id "
                + "SET p.like_count = pm.like_count "
                + "WHERE p.like_count != pm.like_count AND p.deleted_at IS NULL"
        ).executeUpdate();
        log.info("[MetricsReconcile] 2단계 완료 — 보정된 상품 수: {}", productCorrected);

        // 3단계: order_items 기준 → product_metrics.sales_count/sales_amount 보정
        log.info("[MetricsReconcile] 3단계: sales_count/sales_amount 대사 시작");
        int salesCorrected = entityManager.createNativeQuery(
            "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) "
                + "SELECT oi.product_id, 0, 0, SUM(oi.quantity), SUM(oi.product_price * oi.quantity), NOW(6) "
                + "FROM order_item oi JOIN orders o ON oi.order_id = o.id "
                + "WHERE o.status != 'CANCELLED' AND o.deleted_at IS NULL "
                + "GROUP BY oi.product_id "
                + "ON DUPLICATE KEY UPDATE "
                + "sales_count = VALUES(sales_count), sales_amount = VALUES(sales_amount), "
                + "updated_at = NOW(6)"
        ).executeUpdate();
        log.info("[MetricsReconcile] 3단계 완료 — 대사 행 수: {}", salesCorrected);

        return RepeatStatus.FINISHED;
    }
}
