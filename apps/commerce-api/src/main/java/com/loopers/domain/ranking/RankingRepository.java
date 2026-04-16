package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    List<ProductRankEntry> findWeeklyRankings(LocalDate weekStartDate, int page, int size);

    List<ProductRankEntry> findMonthlyRankings(LocalDate monthStartDate, int page, int size);
}
