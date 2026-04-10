package com.loopers.infrastructure.metrics;

import com.loopers.application.ranking.ProductMetricsSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ProductMetricsHourlyQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<ProductMetricsSummary> findByMetricHour(LocalDateTime metricHour) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        """
                        SELECT product_id, like_count, sales_count, sales_amount, view_count
                        FROM product_metrics_hourly
                        WHERE metric_hour = :metricHour
                        """
                )
                .setParameter("metricHour", metricHour)
                .getResultList();

        return rows.stream()
                .map(row -> new ProductMetricsSummary(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue()
                ))
                .toList();
    }
}
