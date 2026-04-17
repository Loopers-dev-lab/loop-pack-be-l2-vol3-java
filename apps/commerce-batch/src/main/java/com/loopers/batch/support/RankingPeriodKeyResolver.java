package com.loopers.batch.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class RankingPeriodKeyResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private RankingPeriodKeyResolver() {}

    public static LocalDate parseDate(String requestDate) {
        return LocalDate.parse(requestDate, DATE_FORMAT);
    }

    public static String toYearWeek(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.KOREA);
        int year = date.getYear();
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    public static String toYearMonth(LocalDate date) {
        return date.format(YEAR_MONTH_FORMAT);
    }
}
