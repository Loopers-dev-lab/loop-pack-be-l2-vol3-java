package com.loopers.infrastructure.metrics;

import com.loopers.application.metrics.ProductMetricsEventMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
public class ProductMetricsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void upsert(ProductMetricsEventMessage eventMessage) {
        ZonedDateTime updatedAt = ZonedDateTime.ofInstant(eventMessage.updatedAt(), ZonedDateTime.now().getZone());
        entityManager.createNativeQuery(
                        """
                        INSERT INTO product_metrics (
                            product_id,
                            like_count,
                            sales_count,
                            view_count,
                            version,
                            updated_at,
                            created_at
                        ) VALUES (
                            :productId,
                            :deltaLike,
                            :deltaSales,
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
                .setParameter("productId", eventMessage.productId())
                .setParameter("deltaLike", eventMessage.deltaLike())
                .setParameter("deltaSales", eventMessage.deltaSales())
                .setParameter("deltaView", eventMessage.deltaView())
                .setParameter("version", eventMessage.version())
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
    }
}
