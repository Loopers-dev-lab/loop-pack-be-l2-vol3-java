package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Name(String value) {

    public Name {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름이 비어 있습니다");
        }
        value = value.trim();

        if (value.isEmpty() || value.length() > 50) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름 길이는 1자 이상 50자 이하여야 합니다: " + value.length());
        }

        // 한글/영문/공백/일부 기호만 허용
        // if (!value.matches("[\\p{L} .'-]+")) throw new CoreException(ErrorType.BAD_REQUEST, "이름에 허용되지 않은 문자가 포함되어 있습니다: " + value);
    }
}
