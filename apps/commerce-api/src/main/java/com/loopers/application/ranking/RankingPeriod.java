package com.loopers.application.ranking;

/**
 * 랭킹 조회 기간 타입.
 *
 * <p>설계 결정:
 * 현재 3가지 분기(DAILY, WEEKLY, MONTHLY)이므로 단순 switch 분기로 충분합니다.
 * 추후 기간 타입이 5개 이상으로 늘어나거나, 각 타입별 복잡한 비즈니스 로직이 필요해지면
 * Strategy 패턴으로 리팩토링을 고려합니다.
 */
public enum RankingPeriod {
    /**
     * 일간 랭킹 (Redis ZSET 기반, 실시간성 중요)
     */
    DAILY,

    /**
     * 주간 랭킹 (mv_product_rank_weekly 테이블, 배치 집계)
     */
    WEEKLY,

    /**
     * 월간 랭킹 (mv_product_rank_monthly 테이블, 배치 집계)
     */
    MONTHLY
}