package com.loopers.infrastructure.metrics;

import com.loopers.contract.kafka.ProductMetricsEventMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Repository
public class ProductMetricsHourlyRepository {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @PersistenceContext
    private EntityManager entityManager;

    public void upsert(ProductMetricsEventMessage eventMessage) {
        ZonedDateTime updatedAt = ZonedDateTime.ofInstant(eventMessage.updatedAt(), KOREA_ZONE);
        ZonedDateTime metricHour = updatedAt.withMinute(0).withSecond(0).withNano(0);
        entityManager.createNativeQuery(
                        """
                        INSERT INTO product_metrics_hourly (
                            metric_hour,
                            product_id,
                            like_count,
                            sales_count,
                            sales_amount,
                            view_count,
                            version,
                            updated_at,
                            created_at
                        ) VALUES (
                            :metricHour,
                            :productId,
                            :deltaLike,
                            :deltaSales,
                            :deltaRevenue,
                            :deltaView,
                            :version,
                            :updatedAt,
                            NOW(6)
                        )
                        ON DUPLICATE KEY UPDATE
                            like_count = CASE
                                WHEN (version < :version OR (version = :version AND updated_at <= :updatedAt))
                                THEN like_count + :deltaLike
                                ELSE like_count
                            END,
                            sales_count = CASE
                                WHEN (version < :version OR (version = :version AND updated_at <= :updatedAt))
                                THEN sales_count + :deltaSales
                                ELSE sales_count
                            END,
                            sales_amount = CASE
                                WHEN (version < :version OR (version = :version AND updated_at <= :updatedAt))
                                THEN sales_amount + :deltaRevenue
                                ELSE sales_amount
                            END,
                            view_count = CASE
                                WHEN (version < :version OR (version = :version AND updated_at <= :updatedAt))
                                THEN view_count + :deltaView
                                ELSE view_count
                            END,
                            version = CASE
                                WHEN (version < :version OR (version = :version AND updated_at <= :updatedAt))
                                THEN :version
                                ELSE version
                            END,
                            updated_at = CASE
                                WHEN (version < :version OR (version = :version AND updated_at <= :updatedAt))
                                THEN :updatedAt
                                ELSE updated_at
                            END
                        """
                )
                .setParameter("metricHour", metricHour.toLocalDateTime())
                .setParameter("productId", eventMessage.productId())
                .setParameter("deltaLike", eventMessage.deltaLike())
                .setParameter("deltaSales", eventMessage.deltaSales())
                .setParameter("deltaRevenue", eventMessage.deltaRevenue())
                .setParameter("deltaView", eventMessage.deltaView())
                .setParameter("version", eventMessage.version())
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
    }
}
