package com.loopers.infrastructure.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * product_metrics 보정 스케줄러
 *
 * 이벤트 기반 집계(CatalogMetricsProcessor)는 이벤트 유실/중복 시
 * product_metrics 값이 실제 DB 원본과 drift할 수 있다.
 *
 * DB 원본(product_likes, order_items)을 기준으로
 * product_metrics와 products.like_count를 주기적으로 보정한다.
 *
 * 보정 주기: 매일 00시
 * 보정 방식: DB 원본 COUNT → UPDATE
 *
 * 주의:
 *   이벤트가 아직 미도착 상태에서 보정하면 delta가 오히려 틀어질 수 있음.
 *   따라서 하루 1회 수준이 적절. 긴급 시 수동 보정 API 별도 제공 가능.
 */
@Component
public class MetricsReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricsReconciliationScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public MetricsReconciliationScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * like_count 보정 — product_likes 테이블 기준
     *
     * product_metrics.like_count + products.like_count 모두 보정.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void reconcileLikeCount() {
        log.info("[MetricsReconciliation] like_count 보정 시작");

        int metricsUpdated = jdbcTemplate.update("""
                UPDATE product_metrics pm
                SET pm.like_count = (
                    SELECT COUNT(*)
                    FROM product_likes pl
                    WHERE pl.product_id = pm.product_id
                      AND pl.deleted_at IS NULL
                ),
                pm.updated_at = NOW()
                """);

        int productsUpdated = jdbcTemplate.update("""
                UPDATE products p
                SET p.like_count = (
                    SELECT COUNT(*)
                    FROM product_likes pl
                    WHERE pl.product_id = p.id
                      AND pl.deleted_at IS NULL
                ),
                p.updated_at = NOW()
                """);

        log.info("[MetricsReconciliation] like_count 보정 완료 — metrics={}건, products={}건",
                metricsUpdated, productsUpdated);
    }

    /**
     * sales_count 보정 — order_items 테이블 기준 (확정 주문만)
     */
    @Scheduled(cron = "0 10 0 * * *")
    public void reconcileSalesCount() {
        log.info("[MetricsReconciliation] sales_count 보정 시작");

        int updated = jdbcTemplate.update("""
                UPDATE product_metrics pm
                SET pm.sales_count = (
                    SELECT COALESCE(SUM(oi.quantity), 0)
                    FROM order_items oi
                    JOIN orders o ON o.id = oi.order_id
                    WHERE oi.product_id = pm.product_id
                      AND o.status = 'CONFIRMED'
                ),
                pm.updated_at = NOW()
                """);

        log.info("[MetricsReconciliation] sales_count 보정 완료 — {}건", updated);
    }
}
