package com.loopers.infrastructure.metrics;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {

    Optional<ProductMetricsEntity> findByProductIdAndMetricsDate(Long productId, LocalDate metricsDate);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.likeCount = m.likeCount + 1 WHERE m.productId = :productId AND m.metricsDate = :metricsDate")
    void incrementLikeCount(@Param("productId") Long productId, @Param("metricsDate") LocalDate metricsDate);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.likeCount = m.likeCount - 1 WHERE m.productId = :productId AND m.metricsDate = :metricsDate AND m.likeCount > 0")
    void decrementLikeCount(@Param("productId") Long productId, @Param("metricsDate") LocalDate metricsDate);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.viewCount = m.viewCount + 1 WHERE m.productId = :productId AND m.metricsDate = :metricsDate")
    void incrementViewCount(@Param("productId") Long productId, @Param("metricsDate") LocalDate metricsDate);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.salesCount = m.salesCount + 1 WHERE m.productId = :productId AND m.metricsDate = :metricsDate")
    void incrementSalesCount(@Param("productId") Long productId, @Param("metricsDate") LocalDate metricsDate);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.totalQuantity = m.totalQuantity + :quantity WHERE m.productId = :productId AND m.metricsDate = :metricsDate")
    void incrementTotalQuantity(@Param("productId") Long productId, @Param("metricsDate") LocalDate metricsDate, @Param("quantity") int quantity);
}
