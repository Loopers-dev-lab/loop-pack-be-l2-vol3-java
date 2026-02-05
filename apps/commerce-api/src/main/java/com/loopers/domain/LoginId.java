package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.regex.Pattern;

public class LoginId {
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 12;

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]*$");

    private final String value;

    public LoginId(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 4자에서 12자 사이여야 합니다.");
        }
        if (!ALPHANUMERIC_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    public String getValue() {
        return value;
    }
}
