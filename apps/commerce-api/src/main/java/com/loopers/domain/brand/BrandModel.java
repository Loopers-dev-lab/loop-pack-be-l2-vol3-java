package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "brands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandModel extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    // === 생성 === //

    private BrandModel(String name) {
        this.name = name;
    }

    public static BrandModel create(String name) {
        validateName(name);
        return new BrandModel(name);
    }

    // === 도메인 로직 === //

    public void update(String name) {
        validateName(name);
        this.name = name;
    }

    // === 검증 === //

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 필수값입니다.");
        }
        if (name.length() > 99) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 99자 이하여야 합니다.");
        }
    }
}
