package com.loopers.domain.ranking.repository;

public interface RankingRepository {
    void updateScore(String key, String member, double score);
    void setKeyExpire(String key, long ttlSeconds);
    void carryOverScores(String fromKey, String toKey, double weight);
    boolean existsKey(String key);
}
