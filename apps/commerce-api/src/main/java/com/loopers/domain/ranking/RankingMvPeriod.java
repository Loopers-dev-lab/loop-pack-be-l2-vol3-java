package com.loopers.domain.ranking;

/**
 * 주간/월간 랭킹 MV 조회용 기간 구분. 배치 Job 파라미터 {@code period}와 동일한 대문자 값을 사용한다.
 */
public enum RankingMvPeriod {
    WEEKLY,
    MONTHLY;

    public static RankingMvPeriod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("period는 필수이며 비어 있으면 안 됩니다.");
        }
        try {
            return RankingMvPeriod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("period는 WEEKLY 또는 MONTHLY만 허용됩니다.");
        }
    }
}
