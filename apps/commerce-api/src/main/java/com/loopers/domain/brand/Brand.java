package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Brand {
    private final Long id;
    private final String name;

    private Brand(Long id, String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }
        this.id = id;
        this.name = name;
    }

    public static Brand create(String name) {
        return new Brand(null, name);
    }

    public static Brand of(Long id, String name) {
        return new Brand(id, name);
    }
}
