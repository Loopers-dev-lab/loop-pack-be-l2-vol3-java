package com.loopers.domain.ranking;

import java.util.List;
import java.util.Map;

/**
 * 상품 랭킹 적재를 위한 Port.
 *
 * <p>구현체는 Redis Sorted Set 등 점수 증분 연산을 제공한다.</p>
 */
public interface RankingRepository {

    /**
     * 상품별 점수를 증분한다.
     *
     * @param key           ZSET 키 (예: {@code ranking:v1:all:20260406})
     * @param productScores 상품 ID → 증분할 점수
     */
    void incrementScores(String key, Map<Long, Double> productScores);

    /**
     * 키 존재 여부를 확인한다.
     *
     * @param key Redis 키
     * @return 존재하면 true
     */
    boolean exists(String key);

    /**
     * 상위 N개 항목의 스코어를 조회한다.
     *
     * @param key   Redis 키
     * @param count 조회할 항목 수
     * @return productId(문자열) → score 맵, 데이터가 없으면 빈 맵
     */
    Map<String, Double> readTopScores(String key, int count);

    /**
     * 스코어를 일괄 증분하고 TTL을 설정한다.
     *
     * @param key        Redis 키
     * @param scores     productId(문자열) → 증분할 점수
     * @param ttlSeconds TTL (초)
     */
    void addScores(String key, Map<String, Double> scores, long ttlSeconds);

    /**
     * ZSET에서 상품을 제거한다.
     *
     * @param key        ZSET 키
     * @param productIds 제거할 상품 ID 목록
     */
    void removeMembers(String key, List<Long> productIds);
}
