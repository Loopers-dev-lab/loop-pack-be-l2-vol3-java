package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;

/**
 * 생년월일 Value Object
 *
 * 검증 규칙:
 * - YYYY-MM-DD 형식 (ISO 8601)
 * - 1900-01-01 ~ 현재 날짜
 * - 실제 존재하는 날짜
 * - 만 14세 이상
 */
@Embeddable
public class BirthDate {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final int MIN_AGE = 14;

    @Column(name = "birth_date")
    private LocalDate value;

    protected BirthDate() {}

    public BirthDate(String rawValue) {
        validateNotBlank(rawValue);
        this.value = parseDate(rawValue);
        validateRange(this.value);
    }

    public LocalDate getValue() {
        return value;
    }

    private static void validateNotBlank(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE, "생년월일은 필수입니다.");
        }
    }

    private static LocalDate parseDate(String rawValue) {
        try {
            return LocalDate.parse(rawValue);
        } catch (DateTimeParseException e) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
                    "생년월일은 YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private static void validateRange(LocalDate date) {
        if (date.isBefore(MIN_DATE)) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
                    "생년월일은 " + MIN_DATE + " 이후여야 합니다.");
        }

        if (date.isAfter(LocalDate.now())) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
                    "생년월일은 미래 날짜일 수 없습니다.");
        }

        if (Period.between(date, LocalDate.now()).getYears() < MIN_AGE) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
                    "만 " + MIN_AGE + "세 이상만 가입할 수 있습니다.");
        }
    }
}