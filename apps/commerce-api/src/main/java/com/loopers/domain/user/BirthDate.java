package com.loopers.domain.user;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BirthDate {

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Column(name = "birth_date", nullable = false)
    private String value;

    public BirthDate(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        try {
            LocalDate.parse(value, BIRTH_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.INVALID_BIRTH_DATE_FORMAT);
        }
    }

    public String getValueWithoutHyphen() {
        return value.replace("-", "");
    }
}