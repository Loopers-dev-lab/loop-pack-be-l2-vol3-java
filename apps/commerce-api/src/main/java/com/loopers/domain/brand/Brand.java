package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Entity
@Table(name = "brands")
@Getter
public class Brand extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Version
    private Long version;

    protected Brand() {}

    private Brand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static Brand create(String name, String description) {
        validateName(name);
        validateDescription(description);
        return new Brand(name, description);
    }

    public void updateInfo(String name, String description) {
        validateNotDeleted();
        if (name != null) {
            validateName(name);
            this.name = name;
        }
        if (description != null) {
            validateDescription(description);
            this.description = description;
        }
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public void validateNotDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 필수입니다");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 100자 이하여야 합니다");
        }
    }

    private static void validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 500자 이하여야 합니다");
        }
    }
}
