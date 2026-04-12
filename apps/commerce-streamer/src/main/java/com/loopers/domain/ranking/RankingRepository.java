package com.loopers.domain.ranking;

public interface RankingRepository {

    void incrementScore(String dateKey, Long productId, double score);

    void carryOver(String sourceKey, String destKey, double carryWeight);
}
