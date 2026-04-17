package com.loopers.batch.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

public final class RankingPeriodKeyFactory {

    private RankingPeriodKeyFactory() {
    }

    public static String toWeeklyKey(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        int weekBasedYear = date.get(weekFields.weekBasedYear());
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return "%d-W%02d".formatted(weekBasedYear, week);
    }

    public static String toMonthlyKey(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public static LocalDate weeklyStart(LocalDate date) {
        return date.with(WeekFields.ISO.dayOfWeek(), 1);
    }

    public static LocalDate weeklyEnd(LocalDate date) {
        return weeklyStart(date).plusDays(6);
    }

    public static LocalDate monthlyStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public static LocalDate monthlyEnd(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }
}
