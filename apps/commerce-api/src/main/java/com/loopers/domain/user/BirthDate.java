package com.loopers.domain.user;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record BirthDate(String value) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public BirthDate {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("생년월일은 비어있을 수 없습니다.");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(value, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("생년월일 형식은 yyyy-MM-dd 이어야 합니다.", e);
        }

        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 미래 날짜일 수 없습니다.");
        }
    }
}
