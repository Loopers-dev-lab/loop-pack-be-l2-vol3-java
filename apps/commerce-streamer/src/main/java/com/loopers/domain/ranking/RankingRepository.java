package com.loopers.domain.ranking;

public interface RankingRepository {

    void incrementScore(String dateKey, Long productId, double score);
}
