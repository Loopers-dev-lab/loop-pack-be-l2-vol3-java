package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 랭킹 ZSET 읽기 연산 인터페이스 (commerce-api 전용).
 */
public interface RankingRepository {

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

    record RankingEntry(Long productId, double score) {}
}
