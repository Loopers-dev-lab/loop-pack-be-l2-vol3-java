package com.loopers.support.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

public final class RankingPeriodKeyFactory {

    private RankingPeriodKeyFactory() {
    }

    public static String toDailyKey(LocalDate date) {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE);
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
}
