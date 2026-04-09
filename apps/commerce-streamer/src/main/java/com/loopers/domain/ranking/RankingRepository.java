package com.loopers.domain.ranking;

import java.util.List;

/**
 * 상품 랭킹 적재를 위한 Port.
 *
 * <p>구현체는 Redis Sorted Set 등 점수 증분 연산을 제공한다.</p>
 */
public interface RankingRepository {

    /**
     * 상품별 점수를 증분한다.
     *
     * @param key    ZSET 키 (예: {@code ranking:v1:daily:20260406})
     * @param scores 증분할 랭킹 점수 목록
     */
    void incrementScores(String key, List<RankingScore> scores);

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
     * @return 스코어 내림차순 랭킹 점수 목록, 데이터가 없으면 빈 리스트
     */
    List<RankingScore> readTopScores(String key, int count);

    /**
     * 스코어를 일괄 증분하고 TTL을 설정한다.
     *
     * @param key        Redis 키
     * @param scores     증분할 랭킹 점수 목록
     * @param ttlSeconds TTL (초)
     */
    void addScores(String key, List<RankingScore> scores, long ttlSeconds);

    /**
     * ZSET에서 상품을 제거한다.
     *
     * @param key        ZSET 키
     * @param productIds 제거할 상품 ID 목록
     */
    void removeMembers(String key, List<Long> productIds);
}
