package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyRankingRepository {

    List<Long> findProductIdsByBaseDate(LocalDate baseDate, long offset, long limit);

    long countByBaseDate(LocalDate baseDate);
}
