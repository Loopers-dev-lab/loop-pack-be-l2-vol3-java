package com.loopers.domain.ranking.mv;

import java.time.LocalDate;
import java.util.List;

public interface MvRankingRepository {

    List<MvProductRankWeekly> findWeeklyRankings(LocalDate aggregatedAt, int page, int size);

    List<MvProductRankMonthly> findMonthlyRankings(LocalDate aggregatedAt, int page, int size);
}
