package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RealtimeRankingRepository {
    List<RankingEntry> findDailyRanking(LocalDate date, long offset, long size);
    long countDailyRanking(LocalDate date);
    Long findProductDailyRank(LocalDate date, Long productId);
    List<RankingEntry> findHourlyRanking(LocalDate date, int hour, long offset, long size);
    long countHourlyRanking(LocalDate date, int hour);
}
