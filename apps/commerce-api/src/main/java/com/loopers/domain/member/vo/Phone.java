package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Locale;
import java.util.regex.Pattern;

public record Phone(String value) {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^010-\\d{4}-\\d{4}$");

    private static final String ERROR_NULL_OR_EMPTY = "전화번호는 필수 입력값입니다";
    private static final String ERROR_INVALID_FORMAT = "전화번호는 010-XXXX-XXXX 형식이어야 합니다";

    public Phone {
        validate(value);
    }

    private void validate(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ERROR_NULL_OR_EMPTY);
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ERROR_INVALID_FORMAT);
        }
    }
}
