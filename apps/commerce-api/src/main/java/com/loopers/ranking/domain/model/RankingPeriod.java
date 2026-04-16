package com.loopers.ranking.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;


/**
 * 랭킹 조회 기간 구분
 * - DAILY: 일간 (Redis ZSET 기반)
 * - WEEKLY: 주간 (mv_product_rank_weekly 기반)
 * - MONTHLY: 월간 (mv_product_rank_monthly 기반)
 */
public enum RankingPeriod {

	DAILY, WEEKLY, MONTHLY;


	/**
	 * 문자열 → RankingPeriod 변환
	 * - null/blank → DAILY (기존 요청 호환)
	 * - 지원하지 않는 값 → INVALID_RANKING_PERIOD 예외
	 */
	public static RankingPeriod from(String value) {
		if (value == null || value.isBlank()) {
			return DAILY;
		}
		try {
			return RankingPeriod.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new CoreException(ErrorType.INVALID_RANKING_PERIOD);
		}
	}

}
