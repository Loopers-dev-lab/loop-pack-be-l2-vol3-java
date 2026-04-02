package com.loopers.infrastructure.persistence.jpa.metrics;

import com.loopers.domain.metrics.ProductMetrics;

/**
 * ProductMetrics 도메인 객체와 JPA 엔티티 간 변환을 담당.
 */
public class ProductMetricsMapper {

    private ProductMetricsMapper() {}

    public static ProductMetrics toDomain(ProductMetricsJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductMetrics.reconstitute(
            entity.getId(),
            entity.getProductId(),
            entity.getLikeCount(),
            entity.getViewCount(),
            entity.getOrderCount(),
            entity.getOrderTotalAmount(),
            entity.getUpdatedAt(),
            entity.getLastEventTimestamp()
        );
    }

    public static void updateEntity(ProductMetricsJpaEntity entity, ProductMetrics domain) {
        entity.setLikeCount(domain.getLikeCount());
        entity.setViewCount(domain.getViewCount());
        entity.setOrderCount(domain.getOrderCount());
        entity.setOrderTotalAmount(domain.getOrderTotalAmount());
        entity.setLastEventTimestamp(domain.getLastEventTimestamp());
    }
}
