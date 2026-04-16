package com.loopers.domain.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 랭킹 조회 인터페이스.
 * DAILY는 Redis ZSET, WEEKLY/MONTHLY는 RDB MV(Materialized View)에서 조회한다.
 */
public interface RankingRepository {

    // ---- DAILY (Redis ZSET) ----

    /**
     * 지정 날짜의 Top-N 랭킹을 페이지 단위로 조회한다. (ZREVRANGE WITHSCORES)
     */
    List<RankingEntry> getTopRankings(LocalDate date, long start, long end);

    /**
     * 특정 상품의 순위를 조회한다. (ZREVRANK, 0-based)
     *
     * @return 0-based 순위, 없으면 null
     */
    Long getRank(LocalDate date, Long productId);

    /**
     * 전체 랭킹 멤버 수를 반환한다. (ZCARD)
     */
    long getTotalCount(LocalDate date);

    /**
     * 시간 단위 Top-N 랭킹을 조회한다. (ranking:hourly:{yyyyMMddHH})
     */
    List<RankingEntry> getHourlyTopRankings(String hourKey, long start, long end);

    // ---- WEEKLY (mv_product_rank_weekly) ----

    List<MvRankingEntry> getWeeklyTop(LocalDate baseDate, int page, int size);

    long getWeeklyTotal(LocalDate baseDate);

    Optional<LocalDateTime> getWeeklyAggregatedAt(LocalDate baseDate);

    // ---- MONTHLY (mv_product_rank_monthly) ----

    List<MvRankingEntry> getMonthlyTop(YearMonth yearMonth, int page, int size);

    long getMonthlyTotal(YearMonth yearMonth);

    Optional<LocalDateTime> getMonthlyAggregatedAt(YearMonth yearMonth);

    /**
     * Redis ZSET 기반 랭킹 한 엔트리 (DAILY).
     */
    record RankingEntry(Long productId, double score) {
    }

    /**
     * RDB MV 기반 랭킹 한 엔트리 (WEEKLY / MONTHLY). rank_no가 이미 집계되어 있음.
     */
    record MvRankingEntry(Long productId, int rankNo, BigDecimal score) {
    }
}
