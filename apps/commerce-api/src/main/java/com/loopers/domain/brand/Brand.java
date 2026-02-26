package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    private Brand(String name, String description, String logoUrl) {
        this.name = name;
        this.description = description;
        this.logoUrl = logoUrl;
    }

    public static Brand create(String name, String description, String logoUrl) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 필수입니다.");
        }
        if (name.length() > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 100자를 초과할 수 없습니다.");
        }
        return new Brand(name, description, logoUrl);
    }

    public void update(String name, String description, String logoUrl) {
        if (name != null) {
            if (name.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 필수입니다.");
            }
            if (name.length() > 100) {
                throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 100자를 초과할 수 없습니다.");
            }
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (logoUrl != null) {
            this.logoUrl = logoUrl;
        }
    }

    public boolean isDeleted() {
        return this.getDeletedAt() != null;
    }
}
