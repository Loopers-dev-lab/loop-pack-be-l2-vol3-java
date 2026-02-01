package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String address) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE
    );

    public Email {
        if (address == null || address.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email이 비어 있습니다");
        }

        address = address.trim().toLowerCase(Locale.ROOT);

        if (address.length() > 254) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email 길이가 너무 깁니다: " + address.length());
        }
        if (!EMAIL_PATTERN.matcher(address).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 이메일 형식입니다: " + address);
        }
    }
}
