package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * GET 랭킹의 {@code period}/{@code periodKey} 검증. 배치 {@code RankingBatchJobParameters}와 동일 규칙.
 */
public final class RankingMvRequestValidator {

    private static final Pattern WEEKLY_KEY = Pattern.compile("^\\d{4}W\\d{2}$");
    private static final Pattern MONTHLY_KEY = Pattern.compile("^\\d{6}$");

    private RankingMvRequestValidator() {
    }

    public static void validateMutualExclusion(
            Optional<String> dateRaw,
            boolean mvRequested) {
        if (mvRequested && dateRaw.filter(s -> !s.isBlank()).isPresent()) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "주간/월간(period·periodKey) 조회와 date는 함께 사용할 수 없습니다.");
        }
    }

    public static void validateMvPairPresent(boolean periodPresent, boolean periodKeyPresent) {
        if (periodPresent != periodKeyPresent) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "period와 periodKey는 함께 지정해야 합니다.");
        }
    }

    public static RankingMvPeriod parsePeriod(String periodRaw) {
        try {
            return RankingMvPeriod.parse(periodRaw);
        } catch (IllegalArgumentException ex) {
            throw new CoreException(ErrorType.BAD_REQUEST, ex.getMessage());
        }
    }

    public static void validatePeriodKey(RankingMvPeriod period, String periodKeyRaw) {
        if (periodKeyRaw == null || periodKeyRaw.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "periodKey는 필수이며 비어 있으면 안 됩니다.");
        }
        try {
            switch (period) {
                case WEEKLY -> validateWeeklyKey(periodKeyRaw);
                case MONTHLY -> validateMonthlyKey(periodKeyRaw);
            }
        } catch (IllegalArgumentException ex) {
            throw new CoreException(ErrorType.BAD_REQUEST, ex.getMessage());
        }
    }

    private static void validateWeeklyKey(String periodKey) {
        if (!WEEKLY_KEY.matcher(periodKey).matches()) {
            throw new IllegalArgumentException(
                    "period가 WEEKLY일 때 periodKey는 yyyyWww 형식이어야 합니다. 예: 2026W15");
        }
        int week = Integer.parseInt(periodKey.substring(periodKey.indexOf('W') + 1));
        if (week < 1 || week > 53) {
            throw new IllegalArgumentException("주차는 01~53 범위여야 합니다.");
        }
    }

    private static void validateMonthlyKey(String periodKey) {
        if (!MONTHLY_KEY.matcher(periodKey).matches()) {
            throw new IllegalArgumentException(
                    "period가 MONTHLY일 때 periodKey는 yyyyMM 형식이어야 합니다. 예: 202604");
        }
        int month = Integer.parseInt(periodKey.substring(4, 6));
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월은 01~12 범위여야 합니다.");
        }
    }
}
