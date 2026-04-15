package com.loopers.domain.ranking;

/**
 * MV 테이블 조회 결과를 담는 도메인 VO.
 * weekly/monthly 공통으로 사용한다.
 */
public record ProductRankEntry(
    Long productId,
    double totalScore,
    int rankPosition
) {}
