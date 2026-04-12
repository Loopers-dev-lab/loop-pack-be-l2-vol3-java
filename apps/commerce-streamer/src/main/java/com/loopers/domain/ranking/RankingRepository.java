package com.loopers.domain.ranking;

public interface RankingRepository {

    void incrementScore(String date, Long productId, double delta);
}
