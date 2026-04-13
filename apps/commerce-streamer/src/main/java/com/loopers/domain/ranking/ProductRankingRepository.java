package com.loopers.domain.ranking;

public interface ProductRankingRepository {

    void incrementScore(String key, Long productId, double score);

    boolean exists(String key);

    void setTtlIfAbsent(String key, long ttlSeconds);

    /**
     * destKey = (destKey * destWeight) ∪ (sourceKey * sourceWeight)
     * destKey 자기 자신을 포함시키면 기존 점수를 보존하면서 sourceKey의 가중치 점수를 합산할 수 있다.
     */
    void unionStoreWithWeights(String destKey, double destWeight, String sourceKey, double sourceWeight);

    /**
     * 플래그 키를 SETNX로 설정한다. 이미 존재하면 false를 반환.
     * 스케줄러 멱등성 보장에 사용한다.
     */
    boolean markIfAbsent(String key, long ttlSeconds);
}
