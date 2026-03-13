package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Embeddable
public class BirthDate {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Column(name = "birth_date", nullable = false)
    private LocalDate value;

    protected BirthDate() {}

    public BirthDate(LocalDate value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "생년월일은 필수입니다.");
        }
        this.value = value;
    }

    public static BirthDate from(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "생년월일은 필수입니다.");
        }
        try {
            return new BirthDate(LocalDate.parse(dateString, FORMATTER));
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "생년월일은 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    public LocalDate value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BirthDate birthDate)) return false;
        return Objects.equals(value, birthDate.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }
}
