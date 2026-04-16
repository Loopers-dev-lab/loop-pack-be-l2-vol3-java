package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDailyId;
import com.loopers.domain.metrics.ProductMetricsDailyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * 일별 상품 지표 JPA Repository.
 *
 * <p>Native upsert 쿼리로 INSERT ON DUPLICATE KEY UPDATE 수행.
 * 키는 (metric_date, product_id) 복합.</p>
 */
public interface ProductMetricsDailyJpaRepository
        extends JpaRepository<ProductMetricsDailyModel, ProductMetricsDailyId> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily " +
            "(product_id, metric_date, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, :metricDate, :count, 0, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + :count, updated_at = NOW()",
            nativeQuery = true)
    void incrementViewCountBy(@Param("productId") Long productId,
                              @Param("metricDate") LocalDate metricDate,
                              @Param("count") int count);

    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily " +
            "(product_id, metric_date, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, :metricDate, 0, 1, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()",
            nativeQuery = true)
    void incrementLikeCount(@Param("productId") Long productId,
                            @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily " +
            "(product_id, metric_date, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, :metricDate, 0, 0, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0), updated_at = NOW()",
            nativeQuery = true)
    void decrementLikeCount(@Param("productId") Long productId,
                            @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily " +
            "(product_id, metric_date, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, :metricDate, 0, 0, 1, :amount, NOW()) " +
            "ON DUPLICATE KEY UPDATE order_count = order_count + 1, " +
            "order_amount = order_amount + :amount, updated_at = NOW()",
            nativeQuery = true)
    void incrementOrderCount(@Param("productId") Long productId,
                             @Param("metricDate") LocalDate metricDate,
                             @Param("amount") long amount);
}
