package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    List<Long> findProductIdsByRank(LocalDate date, long offset, long limit);

    long countByDate(LocalDate date);
}
