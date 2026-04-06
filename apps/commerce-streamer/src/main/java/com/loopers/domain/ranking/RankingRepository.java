package com.loopers.domain.ranking;

public interface RankingRepository {

    /**
     * ZADD: 합성 score를 그대로 덮어쓴다 (누적 X). ledger가 source of truth.
     */
    void putScore(String key, Long productId, double compositeScore, long ttlSeconds);
}
