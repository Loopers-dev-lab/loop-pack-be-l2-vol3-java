package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record BrandName(String value) {
    public BrandName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어 있을 수 없습니다");
        }
        value = value.trim();
        if (value.length() > 50) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 1~50자여야 합니다");
        }
    }
}
