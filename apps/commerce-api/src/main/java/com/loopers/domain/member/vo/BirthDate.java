package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record BirthDate(LocalDate value) {

    private static final DateTimeFormatter BIRTH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public BirthDate {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }
        if (value.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래 날짜일 수 없습니다.");
        }
    }

    public String toFormattedString() {
        return value.format(BIRTH_DATE_FORMAT);
    }
}
