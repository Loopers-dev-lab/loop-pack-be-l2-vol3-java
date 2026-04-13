package com.loopers.infrastructure.metrics.persistence;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductScoreProjection;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Query("SELECT new com.loopers.domain.metrics.ProductScoreProjection("
            + "m.productId, "
            + "CAST(SUM(m.viewCount * 0.1 + m.likeCount * 0.2 + m.orderCount * 0.7) AS double)) "
            + "FROM ProductMetrics m "
            + "WHERE m.metricHour BETWEEN :start AND :end "
            + "GROUP BY m.productId "
            + "ORDER BY SUM(m.viewCount * 0.1 + m.likeCount * 0.2 + m.orderCount * 0.7) DESC")
    List<ProductScoreProjection> findTopScores(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
}
