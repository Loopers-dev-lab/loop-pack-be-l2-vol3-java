package com.loopers.domain.ranking;

public interface RankingRepository {

    void incrementScore(String dateKey, Long productId, double score);

    void carryOverScores(String fromDateKey, String toDateKey, double weight);
}
