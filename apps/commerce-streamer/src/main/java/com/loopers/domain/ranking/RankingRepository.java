package com.loopers.domain.ranking;

import java.time.LocalDate;

public interface RankingRepository {
    void incrementScore(LocalDate date, Long productDbId, double score);
}
