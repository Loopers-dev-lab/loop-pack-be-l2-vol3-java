package com.loopers.infrastructure.metrics;

import com.loopers.application.ranking.ProductDailyMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class ProductMetricsDailyQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<ProductDailyMetrics> findByMetricDate(LocalDate metricDate) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        """
                        SELECT product_id, like_count, sales_count, view_count
                        FROM product_metrics_daily
                        WHERE metric_date = :metricDate
                        """
                )
                .setParameter("metricDate", metricDate)
                .getResultList();

        return rows.stream()
                .map(row -> new ProductDailyMetrics(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue()
                ))
                .toList();
    }
}
