package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.EqualsAndHashCode;

@Embeddable
@EqualsAndHashCode
public class BirthDate {

    private static final DateTimeFormatter DATE_STRING_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private LocalDate birthDate;

    protected BirthDate() {}

    public BirthDate(LocalDate birthDate) {
        validate(birthDate);
        this.birthDate = birthDate;
    }

    private void validate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST,"생년월일은 필수 입력값입니다.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST,"생년월일은 과거 날짜여야 합니다.");
        }
    }

    public String toDateString() {
        return birthDate.format(DATE_STRING_FORMATTER);
    }

    public LocalDate getDate() {
        return birthDate;
    }
}
