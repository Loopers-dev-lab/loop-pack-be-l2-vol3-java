package com.loopers.domain.ranking;

import java.time.Duration;

/**
 * 랭킹 점수 저장
 */
public interface RankingWriteRepository {

    /**
     * 계산된 총점을 랭킹 저장소에 반영한다.
     *
     * @param key 일간 랭킹 키
     * @param member 상품 member 문자열
     * @param score 계산된 총점
     * @param ttl 키 TTL
     */
    void upsertScore(String key, String member, double score, Duration ttl);
}
