package com.loopers.domain.ranking;

public interface RankingRepository {

    void incrementScore(String key, Long productId, double score);

    void setTtl(String key, long seconds);
}
