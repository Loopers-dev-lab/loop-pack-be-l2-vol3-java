package com.loopers.domain.rank;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankingKeyGenerator {

    private static final DateTimeFormatter WEEK_FMT = DateTimeFormatter.ofPattern("YYYY'W'ww");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    public static String weeklyPeriodKey(LocalDate date) {
        return date.format(WEEK_FMT);
    }

    public static String monthlyPeriodKey(LocalDate date) {
        return date.format(MONTH_FMT);
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate weekEnd(LocalDate date) {
        return weekStart(date).plusDays(6);
    }

    public static LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public static LocalDate monthEnd(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }
}
