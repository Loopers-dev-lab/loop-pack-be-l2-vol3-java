package com.loopers.domain.category;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.UUID;

public record Category(
        UUID id,
        String name
) {
    public Category(String name) {
        this(null, name);
    }

    public Category {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카테고리 이름은 필수입니다.");
        }
    }
}
