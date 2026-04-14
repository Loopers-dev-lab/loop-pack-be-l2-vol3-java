package com.loopers.domain.ranking;

public enum RankingType {
    DAILY,
    WEEKLY,
    MONTHLY;

    public int getDays() {
        return switch (this) {
            case DAILY -> 1;
            case WEEKLY -> 7;
            case MONTHLY -> 30;
        };
    }
}
