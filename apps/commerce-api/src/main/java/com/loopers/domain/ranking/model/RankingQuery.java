package com.loopers.domain.ranking.model;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record RankingQuery(
        RankingPeriod period,
        LocalDate date,
        int page,
        int size
) {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    public static RankingQuery of(String period, String date, int page, int size) {
        validate(page, size);
        try {
            return new RankingQuery(RankingPeriod.from(period), LocalDate.parse(date, DATE_FORMAT), page, size);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (yyyyMMdd)");
        }
    }

    private static void validate(int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상 100 이하여야 합니다.");
        }
    }
}
