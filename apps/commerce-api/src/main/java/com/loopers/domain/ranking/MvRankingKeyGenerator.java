package com.loopers.domain.ranking;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MvRankingKeyGenerator {

    private static final DateTimeFormatter WEEK_FMT = DateTimeFormatter.ofPattern("YYYY'W'ww");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    public static String weeklyPeriodKey(LocalDate date) {
        return date.format(WEEK_FMT);
    }

    public static String monthlyPeriodKey(LocalDate date) {
        return date.format(MONTH_FMT);
    }
}
