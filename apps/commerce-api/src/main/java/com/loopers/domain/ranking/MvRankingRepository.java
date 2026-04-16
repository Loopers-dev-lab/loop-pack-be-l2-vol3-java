package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface MvRankingRepository {

    List<RankedProduct> findWeeklyRanking(LocalDate periodStart, LocalDate periodEnd, int offset, int size);

    List<RankedProduct> findMonthlyRanking(LocalDate periodStart, LocalDate periodEnd, int offset, int size);

    long countWeeklyRanking(LocalDate periodStart, LocalDate periodEnd);

    long countMonthlyRanking(LocalDate periodStart, LocalDate periodEnd);
}
