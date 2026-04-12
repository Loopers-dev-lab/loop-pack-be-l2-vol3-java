package com.loopers.domain.ranking;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static RankingPeriod fromString(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ranking period: " + value + ". Allowed: daily, weekly, monthly");
        }
    }
}
