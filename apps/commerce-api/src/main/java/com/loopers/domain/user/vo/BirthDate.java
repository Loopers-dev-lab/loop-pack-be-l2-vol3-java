package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public record BirthDate(LocalDate value) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final String ERROR_NULL_OR_EMPTY = "생년월일은 필수 입력값입니다";
    private static final String ERROR_INVALID_FORMAT = "생년월일 형식이 올바르지 않습니다 (yyyyMMdd)";
    private static final String ERROR_FUTURE_DATE = "생년월일은 미래일 수 없습니다";

    public BirthDate {
        if (value == null) {
            throw new UserValidationException(ERROR_NULL_OR_EMPTY);
        }
        if (value.isAfter(LocalDate.now())) {
            throw new UserValidationException(ERROR_FUTURE_DATE);
        }
    }

    public static BirthDate of(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            throw new UserValidationException(ERROR_NULL_OR_EMPTY);
        }
        try {
            LocalDate date = LocalDate.parse(dateString, FORMATTER);
            return new BirthDate(date);
        } catch (DateTimeParseException e) {
            throw new UserValidationException(ERROR_INVALID_FORMAT);
        }
    }

    public String toYymmdd() {
        return value.format(DateTimeFormatter.ofPattern("yyMMdd"));
    }

    public String toMmdd() {
        return value.format(DateTimeFormatter.ofPattern("MMdd"));
    }

    public String toDdmm() {
        return value.format(DateTimeFormatter.ofPattern("ddMM"));
    }
}
