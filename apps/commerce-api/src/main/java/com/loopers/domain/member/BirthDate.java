package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record BirthDate(LocalDate value) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    public BirthDate {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일이 비어 있습니다");
        }
        if (value.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다: " + value);
        }
        if (value.isBefore(LocalDate.now().minusYears(130))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 너무 오래된 날짜입니다: " + value);
        }
    }

    public static BirthDate fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "birthDate가 비어 있습니다");
        }
        try {
            return new BirthDate(LocalDate.parse(raw.trim(), FMT));
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다: " + raw);
        }
    }

    public String asString() {
        return value.format(FMT);
    }
}
