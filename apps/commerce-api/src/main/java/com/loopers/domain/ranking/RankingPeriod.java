package com.loopers.domain.ranking;

import com.loopers.ranking.RankingKeyGenerator;

import java.time.LocalDate;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY;

    public static RankingPeriod fromString(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ranking period: " + value + ". Allowed: daily, weekly, monthly, quarterly");
        }
    }

    public boolean isMvBased() {
        return this != DAILY;
    }

    public RankPeriodType toMvType() {
        return switch (this) {
            case DAILY -> throw new IllegalStateException("DAILY has no MV period type");
            case WEEKLY -> RankPeriodType.WEEKLY;
            case MONTHLY -> RankPeriodType.MONTHLY;
            case QUARTERLY -> RankPeriodType.QUARTERLY;
        };
    }

    public String periodKey(LocalDate date, boolean previous) {
        return switch (this) {
            case DAILY -> throw new IllegalStateException("DAILY has no MV period key");
            case WEEKLY -> previous ? RankingKeyGenerator.previousWeeklyPeriodKey(date) : RankingKeyGenerator.weeklyPeriodKey(date);
            case MONTHLY -> previous ? RankingKeyGenerator.previousMonthlyPeriodKey(date) : RankingKeyGenerator.monthlyPeriodKey(date);
            case QUARTERLY -> previous ? RankingKeyGenerator.previousQuarterlyPeriodKey(date) : RankingKeyGenerator.quarterlyPeriodKey(date);
        };
    }
}
