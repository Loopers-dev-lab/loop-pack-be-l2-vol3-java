package com.loopers.domain.ranking;

import java.time.LocalDate;

public interface RankingRepository {
    void incrementScore(Long productId, LocalDate date, double increment);
}
