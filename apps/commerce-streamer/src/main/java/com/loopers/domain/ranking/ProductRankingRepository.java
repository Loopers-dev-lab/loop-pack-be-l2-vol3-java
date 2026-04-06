package com.loopers.domain.ranking;

import java.util.Map;

/**
 * 상품 랭킹 적재를 위한 Port.
 *
 * <p>구현체는 Redis Sorted Set 등 원자적 점수 증분 연산을 제공한다.</p>
 */
public interface ProductRankingRepository {

    /**
     * 상품별 점수를 증분한다.
     *
     * @param key           ZSET 키 (예: {@code ranking:v1:all:20260406})
     * @param productScores 상품 ID → 증분할 점수
     */
    void incrementScores(String key, Map<Long, Double> productScores);
}
