package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDaily, Long> {

    Optional<ProductMetricsDaily> findByProductIdAndMetricDate(Long productId, LocalDate date);

    @Query("SELECT p FROM ProductMetricsDaily p WHERE p.metricDate BETWEEN :startDate AND :endDate")
    List<ProductMetricsDaily> findByMetricDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
