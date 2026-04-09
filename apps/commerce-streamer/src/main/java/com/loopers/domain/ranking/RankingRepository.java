package com.loopers.domain.ranking;

import java.time.LocalDate;

public interface RankingRepository {
    void incrementScore(Long productId, double score, LocalDate date);
}
