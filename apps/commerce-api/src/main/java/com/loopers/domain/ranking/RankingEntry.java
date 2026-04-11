package com.loopers.domain.ranking;

/**
 * ZSET 조회 결과를 담는 도메인 record.
 * ZREVRANGE WITHSCORES 결과의 (member, score) 쌍을 자바 객체로 변환할 때 사용.
 * 흐름: Redis ZSET → RankingRedisRepository → RankingEntry → RankingService → RankingFacade
 */
public record RankingEntry(
        Long productId,  // ZSET member (상품 ID)
        double score     // ZSET score (가중치 합산 점수)
) {}
