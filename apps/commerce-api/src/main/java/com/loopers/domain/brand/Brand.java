package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;

/**
 *  브랜드 도메인 객체
 */
public class Brand {

    private final Long id;
    private String name;
    private String description;

    private Brand(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public static Brand create(Long id, String name, String description) {
        validateName(name);

        return new Brand(id, name, description);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Brand.BRAND_NAME_REQUIRED);
        }
    }

    public void update(String name, String description) {
        validateName(name);

        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
