package com.loopers.domain.user;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED_CHARS_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`]+$");

    private PasswordValidator() {
    }

    public static void validate(String rawPassword, LocalDate birthDate) {
        validateNotBlank(rawPassword);
        validateLength(rawPassword);
        validateAllowedChars(rawPassword);
        validateNotContainsBirthday(rawPassword, birthDate);
    }

    private static void validateNotBlank(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다");
        }
    }

    private static void validateLength(String rawPassword) {
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 8~16자여야 합니다");
        }
    }

    private static void validateAllowedChars(String rawPassword) {
        if (!ALLOWED_CHARS_PATTERN.matcher(rawPassword).matches()) {
            throw new IllegalArgumentException("비밀번호는 영문/숫자/특수문자만 가능합니다");
        }
    }

    private static void validateNotContainsBirthday(String rawPassword, LocalDate birthDate) {
        String yyyyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String yyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String MMdd = birthDate.format(DateTimeFormatter.ofPattern("MMdd"));

        if (rawPassword.contains(yyyyMMdd) || rawPassword.contains(yyMMdd) || rawPassword.contains(MMdd)) {
            throw new IllegalArgumentException("비밀번호에 생년월일을 포함할 수 없습니다");
        }
    }
}
