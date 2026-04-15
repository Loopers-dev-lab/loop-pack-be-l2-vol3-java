package com.loopers.domain.ranking;

import java.util.List;

public interface ProductRankMvRepository {

    List<MvProductRankWeekly> findWeeklyRankings(String yearWeek, int page, int size);

    List<MvProductRankMonthly> findMonthlyRankings(String yearMonth, int page, int size);

    void saveAllWeekly(List<MvProductRankWeekly> rankings);

    void saveAllMonthly(List<MvProductRankMonthly> rankings);

    void deleteWeeklyByYearWeek(String yearWeek);

    void deleteMonthlyByYearMonth(String yearMonth);
}
