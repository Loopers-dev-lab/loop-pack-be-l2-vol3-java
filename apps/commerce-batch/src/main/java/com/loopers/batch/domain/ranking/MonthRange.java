package com.loopers.batch.domain.ranking;

import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MonthRange(String yearMonth, LocalDate start, LocalDate end) {

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})$");

    /**
     * {@code ranking_score_ledger.bucket_key} 와의 포맷 공유 계약: {@code yyyyMMdd}.
     * 자세한 내용은 {@link WeekRange} 의 동일 상수 주석 참조.
     */
    private static final DateTimeFormatter BASIC_ISO = DateTimeFormatter.BASIC_ISO_DATE;

    public MonthRange {
        Assert.hasText(yearMonth, "yearMonth must not be blank");
        Assert.notNull(start, "start must not be null");
        Assert.notNull(end, "end must not be null");
    }

    public static MonthRange of(String yearMonth) {
        Assert.hasText(yearMonth, "yearMonth must not be blank");
        Matcher matcher = YEAR_MONTH_PATTERN.matcher(yearMonth);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "yearMonth must be in 'YYYY-MM' format (e.g. 2026-04): " + yearMonth);
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be 1..12: " + month);
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return new MonthRange(yearMonth, start, end);
    }

    public static MonthRange ofPreviousMonth(LocalDate baseDate) {
        Assert.notNull(baseDate, "baseDate must not be null");
        YearMonth previous = YearMonth.from(baseDate).minusMonths(1);
        String yearMonth = String.format("%04d-%02d", previous.getYear(), previous.getMonthValue());
        return of(yearMonth);
    }

    public String startKey() {
        return start.format(BASIC_ISO);
    }

    public String endKey() {
        return end.format(BASIC_ISO);
    }
}
