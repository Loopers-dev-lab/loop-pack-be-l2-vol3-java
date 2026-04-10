package com.loopers.domain.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface RankingMetricsRepository {

    void upsertViewCount(Long productId, LocalDate date, int hour, int viewCount);

    void upsertLikeCount(Long productId, LocalDate date, int hour, int likeCount);

    void upsertOrderRevenue(Long productId, LocalDate date, int hour, BigDecimal revenue);

    /**
     * 지정 날짜의 dirty=true인 (productId, hour) 쌍 목록 조회.
     */
    List<DirtyEntry> findDirtyEntries(LocalDate date);

    /**
     * 특정 상품의 해당 날짜 전체 시간대 집계 합산 (일간 랭킹용).
     */
    RankingMetricsSummary sumByProductIdAndDate(Long productId, LocalDate date);

    /**
     * 특정 상품의 해당 날짜·시간대 집계 합산 (시간별 랭킹용).
     */
    RankingMetricsSummary sumByProductIdAndDateAndHour(Long productId, LocalDate date, int hour);

    /**
     * 해당 (productId, date, hour) 행의 dirty를 false로 전환.
     */
    void clearDirtyByHour(Long productId, LocalDate date, int hour);
}
