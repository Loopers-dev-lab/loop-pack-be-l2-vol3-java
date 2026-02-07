package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record MemberId(String value) {

    // 영문+숫자, 1~10자
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    public MemberId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId가 비어 있습니다");
        }
        value = value.trim();

        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId는 영문+숫자, 1~10자로 이루어져야 합니다: " + value);
        }
    }
}
