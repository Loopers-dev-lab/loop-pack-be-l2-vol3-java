package com.loopers.domain.brand.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record BrandName(String value) {

    private static final int MAX_LENGTH = 50;
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣\\-_\\.]+$");

    public BrandName {
        validate(value);
    }

    private void validate(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }
        if (name.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 " + MAX_LENGTH + "자를 초과할 수 없습니다.");
        }
        if (!VALID_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름에는 영문, 한글, 숫자, '-', '_', '.'만 사용할 수 있습니다.");
        }
    }
}
