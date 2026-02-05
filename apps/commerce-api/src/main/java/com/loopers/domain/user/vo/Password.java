package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Password {

    private enum EncodedMarker { INSTANCE }

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()\\-_=+\\[\\]{}|;:',.<>?/`~]");

    private static final String ERROR_MIN_LENGTH = "비밀번호는 8자 이상이어야 합니다";
    private static final String ERROR_MAX_LENGTH = "비밀번호는 16자 이하여야 합니다";
    private static final String ERROR_UPPERCASE_REQUIRED = "대문자를 포함해야 합니다";
    private static final String ERROR_LOWERCASE_REQUIRED = "소문자를 포함해야 합니다";
    private static final String ERROR_DIGIT_REQUIRED = "숫자를 포함해야 합니다";
    private static final String ERROR_SPECIAL_CHAR_REQUIRED = "특수문자를 포함해야 합니다";

    private final String value;

    public Password(String value) {
        validate(value);
        this.value = value;
    }

    private Password(String value, EncodedMarker marker) {
        this.value = value;
    }

    public static Password ofEncoded(String encodedPassword) {
        return new Password(encodedPassword, EncodedMarker.INSTANCE);
    }

    public String value() {
        return value;
    }

    private void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new UserValidationException(ERROR_MIN_LENGTH);
        }
        if (password.length() > MAX_LENGTH) {
            throw new UserValidationException(ERROR_MAX_LENGTH);
        }
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            throw new UserValidationException(ERROR_UPPERCASE_REQUIRED);
        }
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            throw new UserValidationException(ERROR_LOWERCASE_REQUIRED);
        }
        if (!DIGIT_PATTERN.matcher(password).find()) {
            throw new UserValidationException(ERROR_DIGIT_REQUIRED);
        }
        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            throw new UserValidationException(ERROR_SPECIAL_CHAR_REQUIRED);
        }
    }

    public boolean containsDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        String yymmdd = date.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String mmdd = date.format(DateTimeFormatter.ofPattern("MMdd"));
        String ddmm = date.format(DateTimeFormatter.ofPattern("ddMM"));
        return value.contains(yymmdd) || value.contains(mmdd) || value.contains(ddmm);
    }

    public boolean isEncoded() {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Password password = (Password) o;
        return Objects.equals(value, password.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
