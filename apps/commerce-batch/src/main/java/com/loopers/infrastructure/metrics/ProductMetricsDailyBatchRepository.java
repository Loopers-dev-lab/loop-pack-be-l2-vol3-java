package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.metrics.ProductMetricsDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductMetricsDailyBatchRepository extends JpaRepository<ProductMetricsDaily, ProductMetricsDailyId> {

    @Query("SELECT m FROM ProductMetricsDaily m WHERE m.id.metricsDate BETWEEN :startDate AND :endDate ORDER BY m.id.productId")
    List<ProductMetricsDaily> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
