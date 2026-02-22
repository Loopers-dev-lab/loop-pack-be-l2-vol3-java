package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Brand {
    private final Long id;
    private String name;
    private boolean deleted;

    private Brand(Long id, String name, boolean deleted) {
        validateName(name);
        this.id = id;
        this.name = name;
        this.deleted = deleted;
    }

    public static Brand create(String name) {
        return new Brand(null, name, false);
    }

    public static Brand of(Long id, String name, boolean deleted) {
        return new Brand(id, name, deleted);
    }

    public void update(String name) {
        validateName(name);
        this.name = name;
    }

    public void delete() {
        this.deleted = true;
    }

    public void restore() {
        this.deleted = false;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }
    }
}
