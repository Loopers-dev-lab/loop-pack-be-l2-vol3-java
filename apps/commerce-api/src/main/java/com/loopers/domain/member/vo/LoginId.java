package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record LoginId(String value) {

    private static final String LOGIN_ID_PATTERN = "^[a-zA-Z0-9]+$";

    public LoginId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        if (!value.matches(LOGIN_ID_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용할 수 있습니다.");
        }
    }
}
