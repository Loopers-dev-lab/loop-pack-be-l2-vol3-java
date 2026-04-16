package com.loopers.batch.domain.ranking;

import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record WeekRange(String yearWeek, LocalDate start, LocalDate end) {

    private static final Pattern YEAR_WEEK_PATTERN = Pattern.compile("^(\\d{4})-W(\\d{2})$");

    /**
     * {@code ranking_score_ledger.bucket_key} 와의 <b>포맷 공유 계약</b>: {@code yyyyMMdd}.
     * commerce-streamer 의 {@code com.loopers.support.redis.RankingKeyConstants#dayBucket(LocalDate)}
     * 이 같은 포맷을 쓰며, 이 포맷이 일치하지 않으면 Reader 의 BETWEEN 범위가 조용히 빈 결과를 낸다.
     * streamer 쪽 포맷이 변경되면 이 상수도 함께 바꿔야 하며, 그 반대도 마찬가지다.
     */
    private static final DateTimeFormatter BASIC_ISO = DateTimeFormatter.BASIC_ISO_DATE;

    public WeekRange {
        Assert.hasText(yearWeek, "yearWeek must not be blank");
        Assert.notNull(start, "start must not be null");
        Assert.notNull(end, "end must not be null");
    }

    public static WeekRange of(String yearWeek) {
        Assert.hasText(yearWeek, "yearWeek must not be blank");
        Matcher matcher = YEAR_WEEK_PATTERN.matcher(yearWeek);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "yearWeek must be in 'YYYY-Www' format (e.g. 2026-W15): " + yearWeek);
        }
        int year = Integer.parseInt(matcher.group(1));
        int week = Integer.parseInt(matcher.group(2));
        if (week < 1 || week > 53) {
            throw new IllegalArgumentException("week must be 1..53: " + week);
        }

        // ISO 8601 에서 해당 week-based year 의 1월 4일은 항상 Week 1 에 속한다. 이를 고정 앵커로 삼아
        // 현재 시각(LocalDate.now()) 같은 외부 상태를 pivot 에서 배제한다.
        LocalDate monday = LocalDate.of(year, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
            .with(java.time.DayOfWeek.MONDAY);

        // 주어진 year 에 해당 week 가 존재하지 않으면 (예: 2026-W53) 조정된 결과가 나옴 → 검증
        int resolvedYear = monday.get(IsoFields.WEEK_BASED_YEAR);
        int resolvedWeek = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (resolvedYear != year || resolvedWeek != week) {
            throw new IllegalArgumentException(
                "yearWeek does not exist in ISO calendar: " + yearWeek);
        }

        return new WeekRange(yearWeek, monday, monday.plusDays(6));
    }

    public static WeekRange ofPreviousWeek(LocalDate baseDate) {
        Assert.notNull(baseDate, "baseDate must not be null");
        LocalDate previousWeekDay = baseDate.minusWeeks(1);
        int year = previousWeekDay.get(IsoFields.WEEK_BASED_YEAR);
        int week = previousWeekDay.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String yearWeek = String.format("%04d-W%02d", year, week);
        return of(yearWeek);
    }

    public String startKey() {
        return start.format(BASIC_ISO);
    }

    public String endKey() {
        return end.format(BASIC_ISO);
    }
}
