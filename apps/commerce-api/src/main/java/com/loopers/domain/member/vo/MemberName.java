package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record MemberName(String value) {

    public MemberName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
    }

    public String masked() {
        if (value.length() <= 1) {
            return "*";
        }
        return value.substring(0, value.length() - 1) + "*";
    }
}
