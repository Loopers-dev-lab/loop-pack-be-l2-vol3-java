package com.loopers.infrastructure;

import com.loopers.domain.ProductMetricsDaily;
import com.loopers.domain.ProductMetricsDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ProductMetricsDailyRepository extends JpaRepository<ProductMetricsDaily, ProductMetricsDailyId> {

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics_daily (product_id, metric_date, like_count, view_count, order_line_count, order_amount)
            VALUES (:productId, :metricDate, 1, 0, 0, 0)
            ON DUPLICATE KEY UPDATE like_count = like_count + 1
            """, nativeQuery = true)
    void incrementLikeCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics_daily (product_id, metric_date, like_count, view_count, order_line_count, order_amount)
            VALUES (:productId, :metricDate, -1, 0, 0, 0)
            ON DUPLICATE KEY UPDATE like_count = like_count - 1
            """, nativeQuery = true)
    void decrementLikeCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics_daily (product_id, metric_date, like_count, view_count, order_line_count, order_amount)
            VALUES (:productId, :metricDate, 0, 1, 0, 0)
            ON DUPLICATE KEY UPDATE view_count = view_count + 1
            """, nativeQuery = true)
    void incrementViewCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics_daily (product_id, metric_date, like_count, view_count, order_line_count, order_amount)
            VALUES (:productId, :metricDate, 0, 0, 1, :orderAmount)
            ON DUPLICATE KEY UPDATE order_line_count = order_line_count + 1, order_amount = order_amount + :orderAmount
            """, nativeQuery = true)
    void incrementOrderLineCountAndAmount(
            @Param("productId") Long productId,
            @Param("metricDate") LocalDate metricDate,
            @Param("orderAmount") long orderAmount
    );
}
