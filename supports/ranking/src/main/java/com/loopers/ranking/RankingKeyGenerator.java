package com.loopers.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public final class RankingKeyGenerator {

    private static final String KEY_PREFIX = "rank:all:";
    private static final DateTimeFormatter DAILY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter WEEK_FMT = DateTimeFormatter.ofPattern("YYYY'W'ww");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private RankingKeyGenerator() {}

    public static String dailyKey(LocalDate date) {
        return KEY_PREFIX + date.format(DAILY_FMT);
    }

    public static String shadowKey(LocalDate date) {
        return KEY_PREFIX + date.format(DAILY_FMT) + ":shadow";
    }

    public static String hourlyKey(LocalDate date, int hour) {
        return KEY_PREFIX + date.format(DAILY_FMT) + ":" + String.format("%02d", hour);
    }

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

    public static String previousWeeklyPeriodKey(LocalDate date) {
        return weeklyPeriodKey(date.minusWeeks(1));
    }

    public static String previousMonthlyPeriodKey(LocalDate date) {
        return monthlyPeriodKey(date.minusMonths(1));
    }

    public static String quarterlyPeriodKey(LocalDate date) {
        return date.format(DAILY_FMT);
    }

    public static LocalDate quarterlyStart(LocalDate date) {
        return date.minusDays(89);
    }

    public static LocalDate quarterlyEnd(LocalDate date) {
        return date;
    }

    public static String previousQuarterlyPeriodKey(LocalDate date) {
        return quarterlyPeriodKey(date.minusDays(1));
    }
}
