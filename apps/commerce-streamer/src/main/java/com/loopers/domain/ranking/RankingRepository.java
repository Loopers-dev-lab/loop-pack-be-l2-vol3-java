package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 랭킹 ZSET 쓰기 연산 인터페이스 (commerce-streamer 전용).
 *
 * <p>commerce-api의 RankingRepository와는 별도 정의 (ISP).
 * streamer는 쓰기(ZINCRBY, SADD, SREM)만, api는 읽기(ZREVRANGE, ZREVRANK)만 정의.</p>
 */
public interface RankingRepository {

    /**
     * 특정 상품의 랭킹 점수를 증가시킨다. (ZINCRBY)
     *
     * @param productId 상품 ID
     * @param score     증가시킬 점수 (가중치 적용된 값)
     * @param date      집계 대상 날짜 (이벤트의 occurredAt 기준)
     */
    void incrementScore(Long productId, double score, LocalDate date);

    /**
     * 좋아요 멱등 처리 — 해당 유저의 좋아요가 처음인 경우에만 true 반환. (SADD)
     *
     * @param productId 상품 ID
     * @param userId    유저 ID
     * @param date      집계 대상 날짜
     * @return true: 신규 (ZINCRBY 진행), false: 이미 존재 (스킵)
     */
    boolean addLikeIfAbsent(Long productId, Long userId, LocalDate date);

    /**
     * 좋아요 취소 멱등 처리 — 해당 유저의 좋아요가 존재하던 경우에만 true 반환. (SREM)
     *
     * @param productId 상품 ID
     * @param userId    유저 ID
     * @param date      집계 대상 날짜
     * @return true: 제거됨 (ZINCRBY -score 진행), false: 없었음 (스킵)
     */
    boolean removeLikeIfPresent(Long productId, Long userId, LocalDate date);

    /**
     * 여러 상품의 점수를 Pipeline으로 한 번에 증가시킨다.
     *
     * @param productScores productId → 합산 점수 맵
     * @param date          집계 대상 날짜
     */
    void incrementScoreBatch(Map<Long, Double> productScores, LocalDate date);

    /**
     * 시간 단위 랭킹 점수를 증가시킨다. (ranking:hourly:{yyyyMMddHH})
     *
     * @param productId  상품 ID
     * @param score      증가시킬 점수
     * @param occurredAt 이벤트 발생 시각 (시간 키 생성용)
     */
    void incrementHourlyScore(Long productId, double score, LocalDateTime occurredAt);
}
