package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 랭킹 조회 기간 구분.
 *
 * <ul>
 *     <li>{@link #DAILY} — Redis ZSET 기반 실시간 랭킹</li>
 *     <li>{@link #WEEKLY} — {@code mv_product_rank_weekly} (롤링 7일)</li>
 *     <li>{@link #MONTHLY} — {@code mv_product_rank_monthly} (자연월)</li>
 * </ul>
 */
public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static RankingPeriod fromOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAILY;
        }
        try {
            return RankingPeriod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.VALIDATION_ERROR, "지원하지 않는 period: " + raw);
        }
    }
}
