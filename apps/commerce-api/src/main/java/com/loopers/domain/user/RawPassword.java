package com.loopers.domain.user;

import java.time.LocalDate;
import java.util.regex.Pattern;

public record RawPassword(String value) {

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^[a-zA-Z0-9!@#$%^&*(),.?\":{}|<>]{8,16}$");

    public RawPassword {
        if (value == null || !PASSWORD_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("비밀번호 형식이 올바르지 않습니다");
        }
    }

    public void validateNotContainingBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("생년월일 형식이 올바르지 않습니다");
        }

        String birthDateStr = birthDate.toString().replace("-", "");
        if (value.contains(birthDateStr)) {
            throw new IllegalArgumentException("생년월일은 비밀번호 내에 포함될 수 없습니다");
        }
    }
}
