package com.loopers.domain.ranking;

import java.time.LocalDate;

public interface RankingCarryOverHistoryRepository {

    boolean existsByCarryOverDate(LocalDate date);

    RankingCarryOverHistory save(RankingCarryOverHistory history);
}
