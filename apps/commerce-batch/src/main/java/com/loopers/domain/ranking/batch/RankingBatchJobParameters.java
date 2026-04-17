package com.loopers.domain.ranking.batch;

import java.util.regex.Pattern;

/**
 * 랭킹 MV 배치 Job 파라미터 이름·값 검증·락 키 규칙을 한곳에 둔다.
 */
public final class RankingBatchJobParameters {

    public static final String JOB_NAME = "rankingProductMvJob";
    public static final String JOB_PARAM_PERIOD = "period";
    public static final String JOB_PARAM_PERIOD_KEY = "periodKey";

    public static final String CTX_LOCK_HELD = "ranking.batch.lockHeld";
    public static final String CTX_LOCK_OWNER = "ranking.batch.lockOwnerToken";

    private static final Pattern WEEKLY_KEY = Pattern.compile("^\\d{4}W\\d{2}$");
    private static final Pattern MONTHLY_KEY = Pattern.compile("^\\d{6}$");

    private RankingBatchJobParameters() {
    }

    /**
     * 기간 타입.
     */
    public enum Period {
        WEEKLY,
        MONTHLY;

        public static Period parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("period는 필수이며 비어 있으면 안 됩니다.");
            }
            return Period.valueOf(raw.trim());
        }
    }

    /**
     * JobParameters 검증과 동일한 규칙으로 period·periodKey를 검사한다.
     */
    public static void validate(String periodRaw, String periodKeyRaw) {
        if (periodKeyRaw == null || periodKeyRaw.isBlank()) {
            throw new IllegalArgumentException("periodKey는 필수이며 비어 있으면 안 됩니다.");
        }
        Period period = Period.parse(periodRaw);
        switch (period) {
            case WEEKLY -> validateWeeklyKey(periodKeyRaw);
            case MONTHLY -> validateMonthlyKey(periodKeyRaw);
        }
    }

    /**
     * Redis 락 키를 생성한다.
     *
     * @param period 기간
     * @param periodKey 기간 키
     * @return Redis 락 키
     */
    public static String redisLockKey(String period, String periodKey) {
        return "batch:rank:lock:" + period + ":" + periodKey;
    }

    /**
     * 주간 키를 검증한다.
     *
     * @param periodKey 기간 키
     */
    private static void validateWeeklyKey(String periodKey) {
        if (!WEEKLY_KEY.matcher(periodKey).matches()) {
            throw new IllegalArgumentException(
                    "period가 WEEKLY일 때 periodKey는 yyyyWww 형식이어야 합니다. 예: 2026W15"
            );
        }
        int week = Integer.parseInt(periodKey.substring(periodKey.indexOf('W') + 1));
        if (week < 1 || week > 53) {
            throw new IllegalArgumentException("주차는 01~53 범위여야 합니다.");
        }
    }

    /**
     * 월간 키를 검증한다.
     *
     * @param periodKey 기간 키
     */
    private static void validateMonthlyKey(String periodKey) {
        if (!MONTHLY_KEY.matcher(periodKey).matches()) {
            throw new IllegalArgumentException(
                    "period가 MONTHLY일 때 periodKey는 yyyyMM 형식이어야 합니다. 예: 202604"
            );
        }
        int month = Integer.parseInt(periodKey.substring(4, 6));
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월은 01~12 범위여야 합니다.");
        }
    }
}
