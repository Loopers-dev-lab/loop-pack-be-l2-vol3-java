package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {
    @Column(nullable = false)
    private String name;
    private String description;

    protected Brand() {}

    private Brand(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
    }

    public static Brand create(String name, String description) {
        return new Brand(name, description);
    }

    public void update(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수값입니다.");
        }
    }
}