package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface RankingMetricsJpaRepository extends JpaRepository<RankingMetrics, Long> {

    // 조회수 UPSERT — 행이 없으면 INSERT, 있으면 view_count 누적 + dirty=true
    @Modifying
    @Query(value = "INSERT INTO ranking_metrics (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue, dirty, created_at, updated_at) " +
            "VALUES (:productId, :date, :hour, :viewCount, 0, 0, true, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + :viewCount, dirty = true, updated_at = NOW()",
            nativeQuery = true)
    void upsertViewCount(@Param("productId") Long productId,
                         @Param("date") LocalDate date,
                         @Param("hour") int hour,
                         @Param("viewCount") int viewCount);

    // 좋아요 수 UPSERT — like_count 누적 (CANCELLED 시 음수 전달 가능)
    @Modifying
    @Query(value = "INSERT INTO ranking_metrics (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue, dirty, created_at, updated_at) " +
            "VALUES (:productId, :date, :hour, 0, :likeCount, 0, true, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE like_count = like_count + :likeCount, dirty = true, updated_at = NOW()",
            nativeQuery = true)
    void upsertLikeCount(@Param("productId") Long productId,
                         @Param("date") LocalDate date,
                         @Param("hour") int hour,
                         @Param("likeCount") int likeCount);

    // 주문 매출 UPSERT — order_revenue 누적
    @Modifying
    @Query(value = "INSERT INTO ranking_metrics (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue, dirty, created_at, updated_at) " +
            "VALUES (:productId, :date, :hour, 0, 0, :revenue, true, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE order_revenue = order_revenue + :revenue, dirty = true, updated_at = NOW()",
            nativeQuery = true)
    void upsertOrderRevenue(@Param("productId") Long productId,
                            @Param("date") LocalDate date,
                            @Param("hour") int hour,
                            @Param("revenue") BigDecimal revenue);

    // dirty=true인 (productId, metricsHour) 쌍 조회
    @Query("SELECT new com.loopers.domain.ranking.DirtyEntry(rm.productId, rm.metricsHour) " +
            "FROM RankingMetrics rm WHERE rm.dirty = true AND rm.metricsDate = :date")
    List<com.loopers.domain.ranking.DirtyEntry> findDirtyEntries(@Param("date") LocalDate date);

    // 특정 상품의 해당 날짜 전체 시간대 합산 (일간 랭킹용)
    @Query("SELECT new com.loopers.domain.ranking.RankingMetricsSummary(" +
            "rm.productId, COALESCE(SUM(rm.viewCount), 0), COALESCE(SUM(rm.likeCount), 0), COALESCE(SUM(rm.orderRevenue), 0)) " +
            "FROM RankingMetrics rm WHERE rm.productId = :productId AND rm.metricsDate = :date " +
            "GROUP BY rm.productId")
    com.loopers.domain.ranking.RankingMetricsSummary sumByProductIdAndDate(@Param("productId") Long productId,
                                                                           @Param("date") LocalDate date);

    // 특정 상품의 해당 날짜·시간대 합산 (시간별 랭킹용)
    @Query("SELECT new com.loopers.domain.ranking.RankingMetricsSummary(" +
            "rm.productId, COALESCE(SUM(rm.viewCount), 0), COALESCE(SUM(rm.likeCount), 0), COALESCE(SUM(rm.orderRevenue), 0)) " +
            "FROM RankingMetrics rm WHERE rm.productId = :productId AND rm.metricsDate = :date AND rm.metricsHour = :hour " +
            "GROUP BY rm.productId")
    com.loopers.domain.ranking.RankingMetricsSummary sumByProductIdAndDateAndHour(@Param("productId") Long productId,
                                                                                  @Param("date") LocalDate date,
                                                                                  @Param("hour") int hour);

    // 특정 (productId, date, hour) 행의 dirty=false 전환
    @Modifying
    @Query("UPDATE RankingMetrics rm SET rm.dirty = false, rm.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE rm.productId = :productId AND rm.metricsDate = :date AND rm.metricsHour = :hour AND rm.dirty = true")
    void clearDirtyByHour(@Param("productId") Long productId, @Param("date") LocalDate date, @Param("hour") int hour);
}
