package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class Password {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED_PATTERN =
        Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?]+$");

    private final String value;

    public static Password of(String value, LocalDate birthDate) {
        Password password = new Password(value);
        password.validateAgainst(birthDate);
        return password;
    }

    public Password(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD);
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.INVALID_PASSWORD);
        }
        if (!ALLOWED_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD);
        }
        this.value = value;
    }

    public void validateAgainst(LocalDate birthDate) {
        if (birthDate == null) {
            return;
        }
        String yyyymmdd = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String yymmdd = yyyymmdd.substring(2);
        if (value.contains(yyyymmdd) || value.contains(yymmdd)) {
            throw new CoreException(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }

    public String value() {
        return value;
    }
}
