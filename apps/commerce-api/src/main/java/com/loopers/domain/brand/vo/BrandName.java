package com.loopers.domain.brand.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record BrandName(String value) {
    public BrandName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }
    }
}
