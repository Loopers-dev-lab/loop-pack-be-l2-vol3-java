package com.loopers.batch.job.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;

/**
 * ISO 8601 기반 주/월 경계 계산 순수 함수
 *
 * 모든 연산은 KST(Asia/Seoul) 기준. 테스트 시 시간 고정이 가능하도록
 * 입력은 LocalDate만 받고, ZonedDateTime 필요 시 KST로 고정.
 *
 * ISO 8601 week 규칙:
 * - 한 주는 월요일 시작, 일요일 종료
 * - 주차 번호는 "1월 4일을 포함하는 주"가 W01
 * - 연말·연초 경계: 2025-12-29(월)=2026-W01, 2027-01-01(금)=2026-W53
 */
public final class IsoPeriodMath {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private IsoPeriodMath() {}

    public static LocalDate parseTargetDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) {
            throw new IllegalArgumentException("targetDate is required (format: yyyyMMdd)");
        }
        return LocalDate.parse(yyyyMMdd, DATE_KEY);
    }

    public static String yearWeekOf(LocalDate date) {
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", weekYear, week);
    }

    public static LocalDate startOfIsoWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    public static LocalDate endOfIsoWeek(LocalDate date) {
        return date.with(DayOfWeek.SUNDAY);
    }

    public static String periodMonthOf(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }

    public static LocalDate startOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public static LocalDate endOfMonth(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }

    public static ZonedDateTime startOfDayKst(LocalDate date) {
        return date.atStartOfDay(KST);
    }

    public static ZonedDateTime endOfDayKst(LocalDate date) {
        return date.atTime(23, 59, 59, 999_999_999).atZone(KST);
    }

    /**
     * 배치 runDate 기준으로 해당 주/월이 완결되었는지 판정.
     * periodEnd < runDate 이면 완결.
     */
    public static boolean isPeriodFinalized(LocalDate runDate, LocalDate periodEnd) {
        return runDate.isAfter(periodEnd);
    }
}
