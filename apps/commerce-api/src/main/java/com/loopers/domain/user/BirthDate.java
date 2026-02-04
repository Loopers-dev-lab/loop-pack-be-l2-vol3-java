package com.loopers.domain.user;

import java.text.ParseException;
import java.text.SimpleDateFormat;

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

    @Column(name = "birth_date", nullable = false)
    private String value;

    public BirthDate(String value) {
        if (!isValidDateFormat(value)) {
            throw new CoreException(ErrorType.INVALID_BIRTH_DATE_FORMAT);
        }
        this.value = value;
    }

    public String getValueWithoutHyphen() {
        return value.replace("-", "");
    }

    static boolean isValidDateFormat(String value) {
        try {
            SimpleDateFormat dateFormatParser = new SimpleDateFormat("yyyy-MM-dd");
            dateFormatParser.setLenient(false);
            dateFormatParser.parse(value);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
